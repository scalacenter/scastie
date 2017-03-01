package com.olegych.scastie
package sbt

import instrumentation._

import api._
import ScalaTargetType._

import scala.meta.parsers.Parsed

import org.scalafmt.{Scalafmt, Formatted}
import org.scalafmt.config.{ScalafmtConfig, ScalafmtRunner}

import upickle.default.{read => uread, Reader}

import akka.actor.{Actor, ActorRef}

import scala.concurrent._
import scala.concurrent.duration._
import java.util.concurrent.{TimeoutException, Callable, FutureTask, TimeUnit}

import scala.util.control.NonFatal
import org.slf4j.LoggerFactory
import java.io.{PrintWriter, StringWriter}

class SbtActor(runTimeout: FiniteDuration, production: Boolean) extends Actor {
  private var sbt = new Sbt()
  private val log = LoggerFactory.getLogger(getClass)

  private def format(code: String,
                     worksheetMode: Boolean): Either[String, String] = {
    log.info(s"format (worksheetMode: $worksheetMode)")
    log.info(code)

    val config =
      if (worksheetMode)
        ScalafmtConfig.default.copy(runner = ScalafmtRunner.sbt)
      else
        ScalafmtConfig.default

    Scalafmt.format(code, style = config) match {
      case Formatted.Success(formattedCode) => Right(formattedCode)
      case Formatted.Failure(failure) => {
        val errors = new StringWriter()
        failure.printStackTrace(new PrintWriter(errors))
        val fullStack = errors.toString()
        Left(fullStack)
      }
    }
  }

  override def preStart(): Unit = warmUp()
  override def postStop(): Unit = sbt.exit()

  private def warmUp(): Unit = {
    if (production) {
      log.info("warming up sbt")
      val Right(in) = instrument(Inputs.default)
      sbt.eval("run", in, (line, _, _) => log.info(line), reload = false)
    }
  }

  private def instrument(inputs: Inputs): Either[InstrumentationFailure, Inputs] = {
    if (inputs.worksheetMode) {
      instrumentation.Instrument(inputs.code, inputs.target).right.map(instrumented =>
        inputs.copy(code = instrumented)
      )
    } else Right(inputs)
  }

  private def run(snippetId: SnippetId, inputs: Inputs, ip: String, login: Option[String], 
                  progressActor: ActorRef, snippetActor: ActorRef, forcedProgramMode: Boolean) = {
    val scalaTargetType = inputs.target.targetType
    val isScalaJs = inputs.target.targetType == ScalaTargetType.JS

    def eval(command: String, reload: Boolean) =
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

    def timeout(duration: FiniteDuration): Unit = {
      log.info(s"restarting sbt: $inputs")
      progressActor !
        SnippetProgress(
          snippetId = snippetId,
          userOutput = None,
          sbtOutput = None,
          compilationInfos = List(
            Problem(
              Error,
              line = None,
              message = s"timed out after $duration"
            )
          ),
          instrumentations = Nil,
          runtimeError = None,
          done = true,
          timeout = true,
          forcedProgramMode = false
        )

      sbt.kill()
      sbt = new Sbt()
      warmUp()
    }

    log.info(s"== updating $snippetId ==")

    val sbtReloadTime = 40.seconds
    if (sbt.needsReload(inputs)) {
      withTimeout(sbtReloadTime)(eval("compile", reload = true))(
        timeout(sbtReloadTime))
    }

    log.info(s"== running $snippetId ==")

    withTimeout(runTimeout)({
      scalaTargetType match {
        case JVM | Dotty | Native | Typelevel => eval("run", reload = false)
        case JS => eval("fastOptJS", reload = false)
      }
    })(timeout(runTimeout))

    log.info(s"== done  $snippetId ==")
  }

  def receive = {
    case FormatRequest(code, worksheetMode) => {
      sender ! FormatResponse(format(code, worksheetMode))
    }
    case SbtTask(snippetId, inputs, ip, login, progressActor) => {
      log.info("login: {}, ip: {} run {}", login, ip, inputs)

      instrument(inputs) match {
        case Right(inputs0) => {
          run(snippetId, inputs0, ip, login, progressActor, sender, forcedProgramMode = false)
        } 
        case Left(error) => {
          def signalError(message: String, line: Option[Int]): Unit = {
            val progress = SnippetProgress(
              snippetId = snippetId,
              userOutput = None,
              sbtOutput = None,
              compilationInfos = List(Problem(Error, line, message)),
              instrumentations = Nil,
              runtimeError = None,
              done = true,
              timeout = false,
              forcedProgramMode = false
            )  

            progressActor ! progress
            sender ! progress
          }

          error match { 
            case HasMainMethod => {
              run(snippetId, inputs.copy(worksheetMode = false), ip, login, progressActor, sender, forcedProgramMode = true)
            }
            case UnsupportedDialect => 
              signalError("The worksheet mode does not support this Scala target", None)

            case ParsingError(Parsed.Error(pos, message, _)) => {
              val lineOffset = getLineOffset(worksheetMode = true)

              signalError(message, Some(pos.start.line + lineOffset))
            }

          }
        }
      }
    }
  }

  private def withTimeout(timeout: Duration)(block: ⇒ Unit)(
      onTimeout: => Unit): Unit = {
    val task = new FutureTask(new Callable[Unit]() { def call = block })
    val thread = new Thread(task)
    try {
      thread.start()
      task.get(timeout.toMillis, TimeUnit.MILLISECONDS)
    } catch {
      case e: TimeoutException ⇒ onTimeout
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
      isScalaJs: ): (String, Boolean, Boolean) => Unit = {
    (line, done, reload) =>
      {
        val lineOffset = getLineOffset(worksheetMode)

        val problems = extractProblems(line, lineOffset)
        val instrumentations =
          extract[List[api.Instrumentation]](line, report = true)
        val runtimeError = extractRuntimeError(line, lineOffset)
        val sbtOutput = extract[sbtapi.SbtOutput](line)

        // look like our sbt logger is not catching all messages
        val sbtMessages = List(
          "[info]",
          "[success]",
          "[error]",
          "[warn]"
        )

        val isSbtMessage = sbtMessages.exists(message => line.startsWith(message))

        val userOutput =
          if (problems.isEmpty
              && instrumentations.isEmpty
              && runtimeError.isEmpty
              && !done
              && !isSbtMessage
              && sbtOutput.isEmpty)
            Some(line)
          else None

        if(done) {

        }

        val progress = SnippetProgress(
          snippetId = snippetId,
          userOutput = userOutput,
          sbtOutput = if(isSbtMessage) Some(line) else sbtOutput.map(_.line),
          compilationInfos = problems.getOrElse(Nil),
          instrumentations = instrumentations.getOrElse(Nil),
          runtimeError = runtimeError,
          done = done && !reload,
          timeout = false,
          forcedProgramMode = forcedProgramMode
        )

        progressActor ! progress
        snippetActor ! progress
      }
  }

  private def extractProblems(line: String,
                              lineOffset: Int): Option[List[api.Problem]] = {
    val sbtProblems = extract[List[sbtapi.Problem]](line)

    def toApi(p: sbtapi.Problem): api.Problem = {
      val severity = p.severity match {
        case sbtapi.Info => api.Info
        case sbtapi.Warning => api.Warning
        case sbtapi.Error => api.Error
      }

      api.Problem(severity, p.line.map(_ + lineOffset), p.message)
    }

    sbtProblems.map(_.map(toApi))
  }

  def extractRuntimeError(line: String,
                          lineOffset: Int): Option[api.RuntimeError] = {
    extract[sbtapi.RuntimeError](line).map {
      case sbtapi.RuntimeError(message, line, fullStack) =>
        api.RuntimeError(message, line.map(_ + lineOffset), fullStack)
    }
  }

  private def extract[T: Reader](line: String,
                                 report: Boolean = false): Option[T] = {
    try { Some(uread[T](line)) } catch {
      case NonFatal(e: scala.MatchError) => {
        if (report) {
          println("---")
          println(line)
          println("---")
          e.printStackTrace()
          println("---")
        }

        None
      }
      case NonFatal(_) => None
    }
  }
}
