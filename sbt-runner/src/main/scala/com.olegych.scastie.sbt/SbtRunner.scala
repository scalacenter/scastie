package com.olegych.scastie.sbt

import com.olegych.scastie.instrumentation._
import com.olegych.scastie.api._
import ScalaTargetType._

import scala.meta.parsers.Parsed
import upickle.default.{Reader, read => uread, write => uwrite}
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
                  forcedProgramMode: Boolean): Boolean = {

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
    val compilationRequired = scalaTargetType match {
      case Stainless => false
      case _ => sbt.needsReload(inputs)
    }
    val reloadError =
      if (compilationRequired) {
        log.info(s"== updating $snippetId ==")

        withTimeout(sbtReloadTime)(eval("compile", reload = true))(
          timeout(sbtReloadTime)
        )
      } else false

    if (!reloadError) {
      log.info(s"== running $snippetId ==")

      val res = withTimeout(runTimeout)({
        scalaTargetType match {
          case JVM | Dotty | Native | Typelevel =>
            eval("run", reload = false)

          case JS =>
            eval("fastOptJS", reload = false)

          case Stainless =>
            log.info(s"RUNNING STAINLESS")

            log.info(s"inputs's code = ${inputs.code}")
            val file = java.io.File.createTempFile("user-code", ".scala")
            file.deleteOnExit()
            java.nio.file.Files.write(file.toPath, inputs.code.toString.getBytes)

            /*
             * val lines = java.nio.file.Files.lines(file.toPath)
             * log.info(s"just wrote this: " + (lines.toArray mkString "\n"))
             */

            import scala.sys.process._
            val jsonFile = java.io.File.createTempFile("report", ".json")
            jsonFile.deleteOnExit()
            val status = s"stainless ${file.getAbsolutePath} --json=${jsonFile.getAbsolutePath}".!
            if (status == 0) {
              log.info(s"Success from stainless")
              import org.json4s._
              import org.json4s.native.JsonMethods._
              import java.io.{ BufferedReader, File, FileInputStream, InputStreamReader }
              import java.util.Scanner

              val sc = new Scanner(jsonFile)
              val sb = new StringBuilder
              while (sc.hasNextLine) { sb ++= sc.nextLine }

              val Some(report) = parseOpt(sb.toString)

              def splitPadReform(str: String, pad: String, post: String = "", on: String = "\n") = {
                str split on map { pad + _ + post } mkString on
              }

              def j2Position(value: JValue): Option[Int] = if (value == JString("Unknown")) None else {
                val JInt(end) = value \ "end" \ "line"
                Some(end.intValue)
              }

              for {
                JArray(subReports) <- report \ "verification"
                sub <- subReports
                status = sub \ "status"
                if status == JString("invalid")
                JObject(info) = sub \ "counterexample"
                pos = j2Position(sub \ "pos")
                JString(kind) = sub \ "kind"
                JString(fd) = sub \ "fd"
              } {
                val message: String = {
                  val details: Seq[String] = if (info.nonEmpty) {
                    (info map {
                      case (vd, JString(value)) => s"  when $vd is:\n${splitPadReform(value, "    ")}";
                      case _ => ???
                    })
                  } else {
                    Seq("empty counter example")
                  }
                  s"Counterexample for $kind violation in `$fd`:" + (details mkString "\n")
                }

                log.info(s"Counterexample: $message")

                val progress =
                  SnippetProgress.default
                    .copy(
                      snippetId = Some(snippetId),
                      timeout = false,
                      done = true,
                      compilationInfos = List(
                        Problem(
                          Error,
                          line = pos,
                          message = message
                        )
                      )
                    )

                progressActor ! progress
                snippetActor ! progress
              }

              val progress =
                SnippetProgress.default
                  .copy(
                    snippetId = Some(snippetId),
                    timeout = false,
                    done = true
                  )

              progressActor ! progress
              snippetActor ! progress

            } else {
              log.info(s"ERROR!")
              // TODO get error!!!

              val progress =
                SnippetProgress.default
                  .copy(
                    snippetId = Some(snippetId),
                    timeout = false,
                    done = true,
                    compilationInfos = List(
                      Problem(
                        Error,
                        line = None,
                        message = s"stainless failed"
                      )
                    )
                  )

              progressActor ! progress
              snippetActor ! progress
            }
            true
        }
      })(timeout(runTimeout))

      log.info(s"== done  $snippetId ==")
      res
    } else {
      log.info(s"== reload errors ==")
      false
    }
  }

  override def receive: Receive = {
    case SbtWarmUp =>
      log.info("Got SbtWarmUp")
      if (production) {
        sbt.warmUp()
      }
    case CreateEnsimeConfigRequest =>
      log.info("Generating ensime config file")
      sbt.eval(
        "ensimeConfig",
        defaultConfig,
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

        val problems = extractProblems(line, lineOffset)
        val instrumentations =
          extract[List[Instrumentation]](line, report = true)
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

  private def remapSourceMap(
      snippetId: SnippetId
  )(sourceMapRaw: String): String = {
    try {
      val sourceMap = uread[SourceMap](sourceMapRaw)

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

      uwrite(sourceMap0)
    } catch {
      case e: Exception =>
        e.printStackTrace()
        sourceMapRaw
    }
  }

  private def extractProblems(line: String,
                              lineOffset: Int): Option[List[Problem]] = {
    val problems = extract[List[Problem]](line)

    problems.map(
      _.map(problem => problem.copy(line = problem.line.map(_ + lineOffset)))
    )
  }

  def extractRuntimeError(line: String, lineOffset: Int): Option[RuntimeError] = {
    extract[Option[RuntimeError]](line).flatMap(
      _.map(error => error.copy(line = error.line.map(_ + lineOffset)))
    )
  }

  private def extract[T: Reader](line: String,
                                 report: Boolean = false): Option[T] = {
    try { Option(uread[T](line)) } catch {
      case NonFatal(e: scala.MatchError) =>
        if (report) {
          println("---")
          println(line)
          println("---")
          e.printStackTrace()
          println("---")
        }

        None
      case NonFatal(_) => None
    }
  }

  private[SbtRunner] case class SourceMap(
      version: Int,
      file: String,
      mappings: String,
      sources: List[String],
      names: List[String],
      lineCount: Int
  )
}
