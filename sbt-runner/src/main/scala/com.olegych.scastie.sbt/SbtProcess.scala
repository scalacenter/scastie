package com.olegych.scastie.sbt

import java.nio.file._
import java.time.Instant

import akka.actor.{ActorRef, Cancellable, FSM, Stash}
import akka.pattern.ask
import akka.util.Timeout
import com.olegych.scastie.api._
import com.olegych.scastie.instrumentation.InstrumentedInputs
import com.olegych.scastie.util.ScastieFileUtil.{slurp, write}
import com.olegych.scastie.util._

import scala.concurrent.duration._
import scala.util.Random

object SbtProcess {
  sealed trait SbtState
  case object Initializing extends SbtState
  case object Ready extends SbtState
  case object Reloading extends SbtState
  case object Running extends SbtState

  sealed trait Data
  case class SbtData(currentInputs: Inputs) extends Data
  case class SbtRun(
      snippetId: SnippetId,
      inputs: Inputs,
      isForcedProgramMode: Boolean,
      progressActor: ActorRef,
      snippetActor: ActorRef,
      timeoutEvent: Option[Cancellable]
  ) extends Data
  case class SbtStateTimeout(duration: FiniteDuration, state: SbtState) {
    def message: String = {
      val stateMsg =
        state match {
          case Reloading => "updating build configuration"
          case Running   => "running code"
          case _         => sys.error(s"unexpected timeout in state $state")
        }

      s"timed out after $duration when $stateMsg"
    }

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
}

class SbtProcess(runTimeout: FiniteDuration,
                 reloadTimeout: FiniteDuration,
                 isProduction: Boolean,
                 javaOptions: Seq[String],
                 customSbtDir: Option[Path] = None)
    extends FSM[SbtProcess.SbtState, SbtProcess.Data]
    with Stash {
  import ProcessActor._
  import SbtProcess._
  import context.dispatcher

  private var progressId = 0L

  def sendProgress(run: SbtRun, _p: SnippetProgress): Unit = {
    progressId += 1
    val p = _p.copy(id = Some(progressId))
    run.progressActor ! p
    implicit val tm = Timeout(10.seconds)
    (run.snippetActor ? p)
      .recover {
        case e =>
          log.error(e, s"error while saving progress $p")
      }
  }

  private val sbtDir: Path =
    customSbtDir.getOrElse(Files.createTempDirectory("scastie"))
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

  startWith(
    Initializing, {
      InstrumentedInputs(Inputs.default) match {
        case Right(instrumented) =>
          val inputs = instrumented.inputs
          setInputs(inputs)
          val p = process
          log.info(s"started process ${p}")
          SbtData(inputs)
        case e => sys.error("failed to instrument default input: " + e)
      }
    }
  )

  private lazy val process = {
    val sbtOpts =
      (javaOptions ++ Seq(
        "-Djline.terminal=jline.UnsupportedTerminal",
        "-Dsbt.log.noformat=true",
        "-Dsbt.banner=false",
      )).mkString(" ")

    val props =
      ProcessActor.props(
        command = List("sbt"),
        workingDir = sbtDir,
        environment = Map(
          "SBT_OPTS" -> sbtOpts
        )
      )

    context.actorOf(props, name = s"sbt-process-$promptUniqueId")
  }

  whenUnhandled {
    case Event(_: SbtTask | _: ProcessOutput, _) =>
      stash()
      stay()
    case Event(timeout: SbtStateTimeout, run: SbtRun) =>
      println("*** timeout ***")

      val progress = timeout.toProgress(run.snippetId)
      sendProgress(run, progress)
      throw new Exception(timeout.message)
  }

  onTransition {
    case _ -> Ready =>
      println("-- Ready --")
      unstashAll()
    case _ -> Initializing =>
      println("-- Initializing --")
    case _ -> Reloading =>
      println("-- Reloading --")
    case _ -> Running =>
      println("-- Running --")
  }

  when(Initializing) {
    case Event(out: ProcessOutput, _) =>
      if (isPrompt(out.line)) {
        goto(Ready)
      } else {
        stay()
      }
  }

  when(Ready) {
    case Event(task @ SbtTask(snippetId, taskInputs, ip, login, progressActor), SbtData(stateInputs)) =>
      println(s"Running: (login: $login, ip: $ip) \n ${taskInputs.code.take(30)}")

      val _sbtRun = SbtRun(
        snippetId = snippetId,
        inputs = taskInputs,
        isForcedProgramMode = false,
        progressActor = progressActor,
        snippetActor = sender(),
        timeoutEvent = None
      )
      sendProgress(_sbtRun, SnippetProgress.default.copy(isDone = false, ts = Some(Instant.now.toEpochMilli), snippetId = Some(snippetId)))

      InstrumentedInputs(taskInputs) match {
        case Right(instrumented) =>
          val sbtRun = _sbtRun.copy(inputs = instrumented.inputs, isForcedProgramMode = instrumented.isForcedProgramMode)
          val isReloading = stateInputs.needsReload(sbtRun.inputs)
          setInputs(sbtRun.inputs)

          instrumented.optionalParsingError.foreach { error =>
            sendProgress(sbtRun, error.toProgress(snippetId).copy(isDone = false))
          }

          if (isReloading) {
            process ! Input("reload;compile/compileInputs")
            gotoWithTimeout(sbtRun, Reloading, reloadTimeout)
          } else {
            gotoRunning(sbtRun)
          }

        case Left(report) =>
          log.info(s"Instrumentation error: ${report.message}")
          val sbtRun = _sbtRun
          setInputs(sbtRun.inputs)
          sendProgress(sbtRun, report.toProgress(snippetId))
          goto(Ready)
      }
  }

  val extractor = new OutputExtractor(
    scalaJsContent _,
    scalaJsSourceMapContent _,
    isProduction,
    promptUniqueId
  )

  when(Reloading) {
    case Event(output: ProcessOutput, sbtRun: SbtRun) =>
      val progress = extractor.extractProgress(output, sbtRun, isReloading = true)
      sendProgress(sbtRun, progress)

      if (progress.isSbtError) {
        throw new Exception("sbt error: " + output.line)
      }

      if (isPrompt(output.line)) {
        gotoRunning(sbtRun)
      } else {
        stay()
      }
  }

  when(Running) {
    case Event(output: ProcessOutput, sbtRun: SbtRun) =>
      val progress = extractor.extractProgress(output, sbtRun, isReloading = false)
      sendProgress(sbtRun, progress)

      if (progress.isDone) {
        sbtRun.timeoutEvent.foreach(_.cancel())
        goto(Ready).using(SbtData(sbtRun.inputs))
      } else {
        stay()
      }
  }

  private def gotoWithTimeout(sbtRun: SbtRun, nextState: SbtState, duration: FiniteDuration): this.State = {

    sbtRun.timeoutEvent.foreach(_.cancel())

    val timeout =
      context.system.scheduler.scheduleOnce(
        duration,
        self,
        SbtStateTimeout(duration, nextState)
      )

    goto(nextState).using(sbtRun.copy(timeoutEvent = Some(timeout)))
  }

  private def gotoRunning(sbtRun: SbtRun): this.State = {
    process ! Input(sbtRun.inputs.target.sbtRunCommand(sbtRun.inputs.isWorksheetMode))
    gotoWithTimeout(sbtRun, Running, runTimeout)
  }

  private def isPrompt(line: String): Boolean = {
    line == promptUniqueId
  }

  // Sbt files setup

  private def setInputs(inputs: Inputs): Unit = {
    val prompt =
      s"""shellPrompt := {_ => println(""); "$promptUniqueId" + "\\n "}"""

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
