package com.olegych.scastie
package sbt

import api._
import ScalaTargetType._

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
      val (in, _) = instrument(Inputs.default)
      sbt.eval("run", in, (line, _, _) => log.info(line), reload = false)
    }
  }

  private def instrument(inputs: Inputs): (Inputs, Boolean) = {
    if (inputs.worksheetMode)
      instrumentation.Instrument(inputs.code) match {
        case Right(instrumented) => (inputs.copy(code = instrumented), false)
        case _ => (inputs.copy(worksheetMode = false), true)
      } else (inputs, false)
  }

  def receive = {
    case FormatRequest(code, worksheetMode) => {
      sender ! FormatResponse(format(code, worksheetMode))
    }
    case SbtTask(id, inputs, ip, login, progressActor) => {
      log.info("login: {}, ip: {} run {}", login, ip, inputs)

      val scalaTargetType = inputs.target.targetType

      val (inputs0, forcedProgramMode) = instrument(inputs)

      def eval(command: String, reload: Boolean) =
        sbt.eval(command,
                 inputs0,
                 processSbtOutput(
                   inputs.worksheetMode,
                   forcedProgramMode,
                   progressActor,
                   id,
                   sender
                 ),
                 reload)

      def timeout(duration: FiniteDuration): Unit = {
        log.info(s"restarting sbt: $inputs")
        progressActor !
          PasteProgress(
            id = id,
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

      log.info(s"== updating $id ==")

      val sbtReloadTime = 40.seconds
      if (sbt.needsReload(inputs0)) {
        withTimeout(sbtReloadTime)(eval("compile", reload = true))(
          timeout(sbtReloadTime))
      }

      log.info(s"== running $id ==")

      withTimeout(runTimeout)({
        scalaTargetType match {
          case JVM | Dotty | Native | Typelevel => eval("run", reload = false)
          case JS => eval("fastOptJs", reload = false)
        }
      })(timeout(runTimeout))

      log.info(s"== done  $id ==")
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

  private def processSbtOutput(
      worksheetMode: Boolean,
      forcedProgramMode: Boolean,
      progressActor: ActorRef,
      id: Int,
      pasteActor: ActorRef): (String, Boolean, Boolean) => Unit = {
    (line, done, reload) =>
      {
        val lineOffset =
          if (worksheetMode) -2
          else 0

        val problems = extractProblems(line, lineOffset)
        val instrumentations =
          extract[List[api.Instrumentation]](line, report = true)
        val runtimeError = extractRuntimeError(line, lineOffset)
        val sbtOutput = extract[sbtapi.SbtOutput](line)

        // sbt plugin is not loaded at this stage. we need to drop those messages
        val initializationMessages = List(
          "[info] Loading global plugins from",
          "[info] Loading project definition from",
          "[info] Set current project to scastie"
        )

        val userOutput =
          if (problems.isEmpty
              && instrumentations.isEmpty
              && runtimeError.isEmpty
              && !done
              && !initializationMessages.exists(
                message => line.startsWith(message))
              && sbtOutput.isEmpty)
            Some(line)
          else None

        val progress = PasteProgress(
          id = id,
          userOutput = userOutput,
          sbtOutput = sbtOutput.map(_.line),
          compilationInfos = problems.getOrElse(Nil),
          instrumentations = instrumentations.getOrElse(Nil),
          runtimeError = runtimeError,
          done = done && !reload,
          timeout = false,
          forcedProgramMode = forcedProgramMode
        )

        progressActor ! progress
        pasteActor ! progress
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
