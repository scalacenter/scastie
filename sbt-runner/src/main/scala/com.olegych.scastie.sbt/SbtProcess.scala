package com.olegych.scastie.sbt

import java.nio.file._
import java.time.Instant
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer, TimerScheduler}
import akka.util.Timeout
import com.olegych.scastie.api._
import com.olegych.scastie.instrumentation.InstrumentedInputs
import com.olegych.scastie.util.ScastieFileUtil.{slurp, write}
import com.olegych.scastie.util._
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.Random

object SbtProcess {

  sealed trait Data
  case class SbtData(currentInputs: Inputs) extends Data
  case class SbtRun(
      snippetId: SnippetId,
      inputs: Inputs,
      isForcedProgramMode: Boolean,
      progressActor: ActorRef[SnippetProgress],
      snippetActor: ActorRef[SnippetProgressAsk],
      timeoutKey: Option[Long]
  ) extends Data

  sealed trait Event

  case class SbtTaskEvent(v: SbtTask) extends Event

  case class ProcessOutputEvent(v: ProcessOutput) extends Event

  case class SbtStateTimeout(duration: FiniteDuration, stateMsg: String) extends Event {
    def message: String = s"timed out after $duration when $stateMsg"

    def toProgress(snippetId: SnippetId): SnippetProgress = {
      SnippetProgress.default.copy(
        ts = Some(Instant.now.toEpochMilli),
        snippetId = Some(snippetId),
        isTimeout = true,
        isDone = true,
        compilationInfos = List(
          Problem(
            Error,
            line = None,
            message = message
          )
        )
      )
    }
  }

  /** Let it die and restart the actor */
  final class LetItDie(msg: String) extends Exception(msg)

  def apply(conf: SbtConf, javaOptions: Seq[String]): Behavior[Event] =
    Behaviors.withStash(100) { buffer =>
      Behaviors.supervise[Event] {
        Behaviors.setup { ctx =>
          Behaviors.withTimers { timers =>
            new SbtProcess(conf, javaOptions)(ctx, buffer, timers)()
          }
        }
      }.onFailure(SupervisorStrategy.restart)
    }
}

import SbtProcess._
class SbtProcess private (
  conf: SbtConf, javaOptions: Seq[String]
)(context: ActorContext[Event], buffer: StashBuffer[Event], timers: TimerScheduler[Event]) {
  import ProcessActor._
  import context.{executionContext, log}

  // context.log is not thread safe
  // https://doc.akka.io/docs/akka/current/typed/logging.html#how-to-log
  private val safeLog = LoggerFactory.getLogger(classOf[SbtProcess])

  private var progressId = 0L

  def sendProgress(run: SbtRun, _p: SnippetProgress): Unit = {
    progressId += 1
    val p = _p.copy(id = Some(progressId))
    run.progressActor ! p
    implicit val tm = Timeout(10.seconds)
    implicit val sc = context.system.scheduler
    run.snippetActor.ask(SnippetProgressAsk(_, p))
      .recover {
        case e =>
          safeLog.error(s"error while saving progress $p", e)
      }
  }

  private val sbtDir: Path = Files.createTempDirectory("scastie")
  private val buildFile = sbtDir.resolve("build.sbt")
  private val promptUniqueId = Random.alphanumeric.take(10).mkString

  private val projectDir = sbtDir.resolve("project")
  Files.createDirectories(projectDir)
  // log.info(s"sbtVersion: $sbtVersion")
  write(projectDir.resolve("build.properties"), s"sbt.version = ${com.olegych.scastie.buildinfo.BuildInfo.sbtVersion}")
  private val pluginFile = projectDir.resolve("plugins.sbt")
  private val codeFile = sbtDir.resolve("src/main/scala/main.scala")
  Files.createDirectories(codeFile.getParent)

  private def scalaJsContent(): Option[String] = {
    slurp(sbtDir.resolve(ScalaTarget.Js.targetFilename))
  }

  private def scalaJsSourceMapContent(): Option[String] = {
    slurp(sbtDir.resolve(ScalaTarget.Js.sourceMapFilename))
  }

  def apply(): Behavior[Event] = initializing(
    InstrumentedInputs(Inputs.default) match {
      case Right(instrumented) =>
        val inputs = instrumented.inputs
        setInputs(inputs)
        val p = process
        log.info(s"started process ${p}")
        SbtData(inputs)
      case e => sys.error("failed to instrument default input: " + e)
    }
  )

  private lazy val process = {
    val sbtOpts =
      (javaOptions ++ Seq(
        "-Djline.terminal=jline.UnsupportedTerminal",
        "-Dsbt.log.noformat=true",
        "-Dsbt.banner=false",
      )).mkString(" ")

    context.spawn(
      ProcessActor(
        replyTo = context.messageAdapter(ProcessOutputEvent),
        command = List("sbt"),
        workingDir = sbtDir,
        environment = Map(
          "SBT_OPTS" -> sbtOpts
        )
      ),
      name = s"sbt-process-$promptUniqueId"
    )
  }

  def unhandled(data: Data): PartialFunction[Event, Behavior[Event]] = {
    case e @ (_: SbtTaskEvent | _: ProcessOutputEvent) =>
      buffer.stash(e)
      Behaviors.same

    case timeout: SbtStateTimeout =>
      data match {
        case run: SbtRun =>
          println("*** timeout ***")
          val progress = timeout.toProgress(run.snippetId)
          sendProgress(run, progress)
          throw new LetItDie(timeout.message)
        case _ =>
          log.error(s"Unexpected timeout $timeout - when data=$data")
          Behaviors.same
      }
  }

  def initializing(data: SbtData): Behavior[Event] = {
    println("-- Initializing --")
    Behaviors.receiveMessage {
      _initializing(data) orElse unhandled(data)
    }
  }

  private def _initializing(data: SbtData): PartialFunction[Event, Behavior[Event]] = {
    case ProcessOutputEvent(out) =>
      if (isPrompt(out.line)) {
        ready(data)
      } else {
        Behaviors.same
      }
  }

  def ready(data: SbtData): Behavior[Event] = {
    println("-- Ready --")
    buffer.unstashAll(Behaviors.receiveMessage {
      _ready(data) orElse unhandled(data)
    })
  }

  private def _ready(data: SbtData): PartialFunction[Event, Behavior[Event]] = {
    case SbtTaskEvent(SbtTask(snippetId, taskInputs, ip, login, progressActor, snippetActor)) =>
      val SbtData(stateInputs) = data
      println(s"Running: (login: $login, ip: $ip) \n ${taskInputs.code.take(30)}")

      val _sbtRun = SbtRun(
        snippetId = snippetId,
        inputs = taskInputs,
        isForcedProgramMode = false,
        progressActor = progressActor,
        snippetActor = snippetActor,
        timeoutKey = None
      )
      sendProgress(_sbtRun, SnippetProgress.default.copy(isDone = false, ts = Some(Instant.now.toEpochMilli), snippetId = Some(snippetId)))

      InstrumentedInputs(taskInputs) match {
        case Right(instrumented) =>
          val sbtRun = _sbtRun.copy(inputs = instrumented.inputs, isForcedProgramMode = instrumented.isForcedProgramMode)
          val isReloading = stateInputs.needsReload(sbtRun.inputs)
          setInputs(sbtRun.inputs)

          if (isReloading) {
            process ! Input("reload;compile/compileInputs")
            gotoWithTimeout(sbtRun, reloading, SbtStateTimeout(conf.sbtReloadTimeout, "updating build configuration"))
          } else {
            gotoRunning(sbtRun)
          }

        case Left(report) =>
          val sbtRun = _sbtRun
          setInputs(sbtRun.inputs)
          sendProgress(sbtRun, report.toProgress(snippetId))
          Behaviors.same
      }
  }

  val extractor = new OutputExtractor(
    scalaJsContent _,
    scalaJsSourceMapContent _,
    conf.remapSourceMapUrlBase,
    promptUniqueId
  )

  def reloading(data: SbtRun): Behavior[Event] = {
    println("-- Reloading --")
    Behaviors.receiveMessage {
      _reloading(data) orElse unhandled(data)
    }
  }

  private def _reloading(sbtRun: SbtRun): PartialFunction[Event, Behavior[Event]] = {
    case ProcessOutputEvent(output) =>
      val progress = extractor.extractProgress(output, sbtRun, isReloading = true)
      sendProgress(sbtRun, progress)

      if (progress.isSbtError) {
        throw new LetItDie("sbt error: " + output.line)
      }

      if (isPrompt(output.line)) {
        gotoRunning(sbtRun)
      } else {
        Behaviors.same
      }
  }

  def running(data: SbtRun): Behavior[Event] = {
    println("-- Running --")
    Behaviors.receiveMessage {
      _running(data) orElse unhandled(data)
    }
  }

  private def _running(sbtRun: SbtRun): PartialFunction[Event, Behavior[Event]] = {
    case ProcessOutputEvent(output) =>
      val progress = extractor.extractProgress(output, sbtRun, isReloading = false)
      sendProgress(sbtRun, progress)

      if (progress.isDone) {
        sbtRun.timeoutKey.foreach(timers.cancel)
        ready(SbtData(sbtRun.inputs))
      } else {
        Behaviors.same
      }
  }

  private[this] var timeoutKey = 0L

  private def gotoWithTimeout(
    sbtRun: SbtRun,
    nextState: SbtRun => Behavior[Event],
    sbtStateTimeout: SbtStateTimeout
  ): Behavior[Event] = {
    sbtRun.timeoutKey.foreach(timers.cancel)
    timeoutKey += 1
    timers.startSingleTimer(timeoutKey, sbtStateTimeout, sbtStateTimeout.duration)
    nextState(sbtRun.copy(timeoutKey = Some(timeoutKey)))
  }

  private def gotoRunning(sbtRun: SbtRun): Behavior[Event] = {
    process ! Input(sbtRun.inputs.target.sbtRunCommand(sbtRun.inputs.isWorksheetMode))
    gotoWithTimeout(sbtRun, running, SbtStateTimeout(conf.runTimeout, "running code"))
  }

  private def isPrompt(line: String): Boolean = {
    line == promptUniqueId
  }

  // Sbt files setup

  private def setInputs(inputs: Inputs): Unit = {
    val prompt =
      s"""shellPrompt := {_ => println("$promptUniqueId"); "> "}"""

    writeFile(pluginFile, inputs.sbtPluginsConfig + "\n")
    writeFile(buildFile, prompt + "\n" + inputs.sbtConfig)
    Files.deleteIfExists(sbtDir.resolve(ScalaTarget.Js.targetFilename))
    Files.deleteIfExists(sbtDir.resolve(ScalaTarget.Js.sourceMapFilename))
    write(codeFile, inputs.code, truncate = true)
  }

  private def writeFile(path: Path, content: String): Unit = {
    if (Files.exists(path)) {
      Files.delete(path)
    }

    Files.write(path, content.getBytes, StandardOpenOption.CREATE_NEW)

    ()
  }

  // private def warmUp(): Unit = {
  //   log.info("warming up sbt")
  //   val Right(in) = SbtRunner.instrument(defaultInputs)
  //   eval("run", in, (line, _, _, _) => log.info(line), reload = false)
  //   log.info("warming up sbt done")
  // }
}
