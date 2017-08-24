package com.olegych.scastie.sbt

import com.olegych.scastie.instrumentation._
import com.olegych.scastie.api._
import com.olegych.scastie.api.ScalaTargetType._

import play.api.libs.json._

import scala.meta.parsers.Parsed
import akka.actor.{Actor, ActorRef}

import scala.concurrent.duration._
import java.util.concurrent.{Callable, FutureTask, TimeUnit, TimeoutException}

import com.olegych.scastie.SbtTask

import scala.util.control.NonFatal
import org.slf4j.LoggerFactory

object SbtRunner {
  def instrument(
      inputs: Inputs
  ): Either[InstrumentationFailure, Inputs] = {
    if (inputs.worksheetMode &&
        inputs.target.targetType != ScalaTargetType.Dotty) {

      Instrument(inputs.code, inputs.target)
        .map(instrumented => inputs.copy(code = instrumented))
    } else Right(inputs)
  }
}

class SbtRunner(runTimeout: FiniteDuration, production: Boolean) extends Actor {

  private case object SbtWarmUp

  private implicit val sbtOutputFormat: OFormat[ConsoleOutput.SbtOutput] =
    ConsoleOutput.ConsoleOutputFormat.formatSbtOutput

  private val defaultConfig = Inputs.default

  private var sbt = new Sbt(defaultConfig)

  private val log = LoggerFactory.getLogger(getClass)

  override def preStart(): Unit = {
    self ! SbtWarmUp
    super.preStart()
  }
  override def postStop(): Unit = {
    sbt.exit()
    super.postStop()
  }

  private def run(snippetId: SnippetId,
                  inputs: Inputs,
                  ip: String,
                  login: Option[String],
                  progressActor: ActorRef,
                  snippetActor: ActorRef,
                  forcedProgramMode: Boolean) = {

    val scalaTargetType = inputs.target.targetType
    val isScalaJs = inputs.target.targetType == ScalaTargetType.JS

    def eval(command: String, reload: Boolean): Boolean =
      sbt.eval(command,
               inputs,
               processSbtOutput(
                 inputs.worksheetMode,
                 forcedProgramMode,
                 progressActor,
                 snippetId,
                 snippetActor,
                 isScalaJs
               ),
               reload)

    def timeout(duration: FiniteDuration): Boolean = {
      log.info(s"restarting sbt: $inputs")

      val timeoutProgress =
        SnippetProgress.default
          .copy(
            snippetId = Some(snippetId),
            timeout = true,
            done = true,
            compilationInfos = List(
              Problem(
                Error,
                line = None,
                message = s"timed out after $duration"
              )
            )
          )

      progressActor ! timeoutProgress
      snippetActor ! timeoutProgress

      sbt.kill()
      sbt = new Sbt(defaultConfig)
      true
    }

    val sbtReloadTime = 40.seconds
    val reloadError =
      if (sbt.needsReload(inputs)) {
        log.info(s"== updating $snippetId ==")

        withTimeout(sbtReloadTime)(eval("compile", reload = true))(
          timeout(sbtReloadTime)
        )
      } else false

    if (!reloadError) {
      log.info(s"== running $snippetId ==")

      withTimeout(runTimeout)({
        eval(inputs.target.sbtRunCommand, reload = false)
      })(timeout(runTimeout))

      log.info(s"== done  $snippetId ==")
    } else {
      log.info(s"== reload errors ==")
    }
  }

  override def receive: Receive = {
    case SbtWarmUp =>
      log.info("Got SbtWarmUp")
      if (production) {
        sbt.warmUp()
      }

    case CreateEnsimeConfigRequest(inputs: Inputs) =>
      log.info("Generating ensime config file")
      sbt.eval(
        "ensimeConfig",
        inputs,
        (line, _, _, _) => {
          log.info(line)
        },
        reload = false
      )
      sender() ! EnsimeConfigResponse(sbt.sbtDir)

    case SbtTask(snippetId, inputs, ip, login, progressActor) =>
      log.info("login: {}, ip: {} run {}", login, ip, inputs)

      SbtRunner.instrument(inputs) match {
        case Right(inputs0) =>
          run(snippetId,
              inputs0,
              ip,
              login,
              progressActor,
              sender,
              forcedProgramMode = false)
        case Left(error) =>
          def signalError(message: String, line: Option[Int]): Unit = {
            val progress =
              SnippetProgress.default
                .copy(
                  snippetId = Some(snippetId),
                  compilationInfos = List(Problem(Error, line, message))
                )

            progressActor ! progress
            sender ! progress
          }

          error match {
            case HasMainMethod =>
              run(snippetId,
                  inputs.copy(worksheetMode = false),
                  ip,
                  login,
                  progressActor,
                  sender,
                  forcedProgramMode = true)
            case UnsupportedDialect =>
              signalError(
                "The worksheet mode does not support this Scala target",
                None
              )

            case ParsingError(Parsed.Error(pos, message, _)) =>
              val lineOffset = getLineOffset(worksheetMode = true)

              signalError(message, Some(pos.start.line + lineOffset))
          }
      }
  }

  private def withTimeout[T](
      timeout: Duration
  )(block: ⇒ T)(onTimeout: => T): T = {
    val task = new FutureTask(new Callable[T]() { def call: T = block })
    val thread = new Thread(task)
    try {
      thread.start()
      task.get(timeout.toMillis, TimeUnit.MILLISECONDS)
    } catch {
      case _: TimeoutException ⇒ onTimeout
    } finally {
      if (thread.isAlive) thread.stop()
    }
  }

  private def getLineOffset(worksheetMode: Boolean): Int =
    if (worksheetMode) -2
    else 0

  private def processSbtOutput(
      worksheetMode: Boolean,
      forcedProgramMode: Boolean,
      progressActor: ActorRef,
      snippetId: SnippetId,
      snippetActor: ActorRef,
      isScalaJs: Boolean
  ): (String, Boolean, Boolean, Boolean) => Unit = {
    (line, done, sbtError, reload) =>
      {
        log.debug(line)

        val lineOffset = getLineOffset(worksheetMode)

        val problems = extractProblems(line, lineOffset, worksheetMode)
        val instrumentations =
          extract[List[Instrumentation]](line)
        val runtimeError = extractRuntimeError(line, lineOffset)
        val sbtOutput = extract[ConsoleOutput.SbtOutput](line)

        // sbt plugin is not loaded at this stage. we need to drop those messages
        val initializationMessages = List(
          "[info] Loading global plugins from",
          "[info] Loading project definition from",
          "[info] Set current project to scastie",
          "[info] Updating {file:",
          "[info] Done updating.",
          "[info] Resolving",
          "[error] Type error in expression"
        )

        val isSbtMessage =
          initializationMessages.exists(message => line.startsWith(message))

        val userOutput =
          if (problems.isEmpty
              && instrumentations.isEmpty
              && runtimeError.isEmpty
              && !done
              && !isSbtMessage
              && sbtOutput.isEmpty)
            Some(line)
          else None

        val (scalaJsContent, scalaJsSourceMapContent) =
          if (done && isScalaJs && problems.isEmpty) {
            (sbt.scalaJsContent(), sbt.scalaJsSourceMapContent())
          } else (None, None)

        val progress = SnippetProgress(
          snippetId = Some(snippetId),
          userOutput = userOutput,
          sbtOutput = if (isSbtMessage) Some(line) else sbtOutput.map(_.line),
          compilationInfos = problems.getOrElse(Nil),
          instrumentations = instrumentations.getOrElse(Nil),
          runtimeError = runtimeError,
          scalaJsContent = scalaJsContent,
          scalaJsSourceMapContent = scalaJsSourceMapContent.map(
            remapSourceMap(snippetId)
          ),
          done = (done && !reload) || sbtError,
          timeout = false,
          sbtError = sbtError,
          forcedProgramMode = forcedProgramMode
        )

        progressActor ! progress.copy(scalaJsContent = None,
                                      scalaJsSourceMapContent = None)
        snippetActor ! progress
      }
  }

  private implicit val formatSourceMap: OFormat[SbtRunner.this.SourceMap] =
    Json.format[SourceMap]

  private case class SourceMap(
      version: Int,
      file: String,
      mappings: String,
      sources: List[String],
      names: List[String],
      lineCount: Int
  )

  private def remapSourceMap(
      snippetId: SnippetId
  )(sourceMapRaw: String): String = {
    Json
      .fromJson[SourceMap](Json.parse(sourceMapRaw))
      .asOpt
      .map { sourceMap =>
        val sourceMap0 =
          sourceMap.copy(
            sources = sourceMap.sources.map(
              source =>
                if (source.startsWith(ScalaTarget.Js.sourceUUID)) {
                  val host =
                    if (production) "https://scastie.scala-lang.org"
                    else "http://localhost:9000"

                  host + snippetId.scalaJsUrl(ScalaTarget.Js.sourceFilename)
                } else source
            )
          )

        Json.prettyPrint(Json.toJson(sourceMap0))
      }
      .getOrElse(sourceMapRaw)
  }

  private def extractProblems(line: String,
                              lineOffset: Int,
                              worksheetMode: Boolean): Option[List[Problem]] = {
    val problems = extract[List[Problem]](line)

    val problemsWithOffset =
      problems.map(
        _.map(problem => problem.copy(line = problem.line.map(_ + lineOffset)))
      )

    def annoying(in: Problem): Boolean = {
      in.severity == Warning &&
      in.message == "a pure expression does nothing in statement position; you may be omitting necessary parentheses"
    }

    if (worksheetMode) problemsWithOffset.map(_.filterNot(annoying))
    else problemsWithOffset
  }

  def extractRuntimeError(line: String, lineOffset: Int): Option[RuntimeError] = {
    extract[RuntimeErrorWrap](line).flatMap(
      _.error.map(error => error.copy(line = error.line.map(_ + lineOffset)))
    )
  }

  private def extract[T: Reads](line: String): Option[T] = {
    try {
      Json.fromJson[T](Json.parse(line)).asOpt
    } catch {
      case NonFatal(e) => None
    }
  }
}
