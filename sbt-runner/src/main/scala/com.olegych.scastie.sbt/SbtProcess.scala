package com.olegych.scastie.sbt

import java.nio.file._

import akka.actor.{ActorRef, Cancellable, FSM, Stash}
import akka.pattern.ask
import akka.util.Timeout
import com.olegych.scastie.api._
import com.olegych.scastie.buildinfo.BuildInfo.sbtVersion
import com.olegych.scastie.instrumentation.InstrumentedInputs
import com.olegych.scastie.util.ScastieFileUtil.{slurp, write}
import com.olegych.scastie.util._
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, ExecutionContext}
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
  ) extends Data {
    private val log = LoggerFactory.getLogger(getClass)
    def sendProgress(p: SnippetProgress)(implicit ec: ExecutionContext): Unit = {
      implicit val tm = Timeout(10.seconds)
      val f = (snippetActor ? p)
        .recover {
          case e =>
            log.error(s"error while saving progress $p", e)
        }
        .map(_ => progressActor ! p)
      //todo remove blocking
      //not sure how though since we need progress updates delivered in order, but waiting for save introduces random delays
      Await.result(f, tm.duration)
    }
  }

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

  // private val log = LoggerFactory.getLogger(getClass)

  private val sbtDir: Path =
    customSbtDir.getOrElse(Files.createTempDirectory("scastie"))
  private val buildFile = sbtDir.resolve("build.sbt")
  private val promptUniqueId = Random.alphanumeric.take(10).mkString

  private val projectDir = sbtDir.resolve("project")
  Files.createDirectories(projectDir)
  // log.info(s"sbtVersion: $sbtVersion")
  write(projectDir.resolve("build.properties"), s"sbt.version = $sbtVersion")
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
          SbtData(inputs)
        case e => sys.error("failed to instrument default input: " + e)
      }
    }
  )

  private val process = {
    val sbtOpts =
      (javaOptions ++ Seq(
        "-Djline.terminal=jline.UnsupportedTerminal",
        "-Dsbt.log.noformat=true"
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
    case Event(_: SbtTask, _) =>
      stash()
      stay
    case Event(timeout: SbtStateTimeout, run: SbtRun) =>
      println("*** timeout ***")

      val progress = timeout.toProgress(run.snippetId)
      run.sendProgress(progress)
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
    case Event(ProcessOutput(line, _), _) =>
      println(line)
      if (isPrompt(line)) {
        goto(Ready)
      } else {
        stay
      }
  }

  when(Ready) {
    case Event(task @ SbtTask(snippetId, taskInputs, ip, login, progressActor), SbtData(stateInputs)) =>
      println(s"Running: (login: $login, ip: $ip) \n $taskInputs")

      val _sbtRun = SbtRun(
        snippetId = snippetId,
        inputs = taskInputs,
        isForcedProgramMode = false,
        progressActor = progressActor,
        snippetActor = sender,
        timeoutEvent = None
      )

      InstrumentedInputs(taskInputs) match {
        case Right(instrumented) =>
          val sbtRun = _sbtRun.copy(inputs = instrumented.inputs, isForcedProgramMode = instrumented.isForcedProgramMode)
          val isReloading = stateInputs.needsReload(sbtRun.inputs)
          setInputs(sbtRun.inputs)

          if (isReloading) {
            process ! Input("reload")
            gotoWithTimeout(sbtRun, Reloading, reloadTimeout)
          } else {
            gotoRunning(sbtRun)
          }

        case Left(report) =>
          val sbtRun = _sbtRun
          setInputs(sbtRun.inputs)
          sbtRun.sendProgress(report.toProgress(snippetId))
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
      sbtRun.sendProgress(progress)

      if (progress.isSbtError) {
        throw new Exception("sbt error: " + output.line)
      }

      if (isPrompt(output.line)) {
        gotoRunning(sbtRun)
      } else {
        stay
      }
  }

  when(Running) {
    case Event(output: ProcessOutput, sbtRun: SbtRun) =>
      val progress = extractor.extractProgress(output, sbtRun, isReloading = false)
      sbtRun.sendProgress(progress)

      if (isPrompt(output.line)) {
        sbtRun.timeoutEvent.foreach(_.cancel())
        goto(Ready).using(SbtData(sbtRun.inputs))
      } else {
        stay
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
    process ! Input(sbtRun.inputs.target.sbtRunCommand)
    gotoWithTimeout(sbtRun, Running, runTimeout)
  }

  private def isPrompt(line: String): Boolean = {
    line.endsWith(promptUniqueId)
  }

  // Sbt files setup

  private def setInputs(inputs: Inputs): Unit = {

    println(s"setInputs $inputs")

    val prompt =
      s"""shellPrompt := (_ => "$promptUniqueId" + System.lineSeparator)"""

    writeFile(pluginFile, inputs.sbtPluginsConfig + ProcessActor.lineSeparator)
    writeFile(buildFile, prompt + ProcessActor.lineSeparator + inputs.sbtConfig)
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
