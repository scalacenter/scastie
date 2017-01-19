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
import System.{lineSeparator => nl}

class SbtActor(runTimeout: FiniteDuration) extends Actor {
  private var sbt = new Sbt()

  private def format(code: String, isInstrumented: Boolean): Option[String] = {
    val config =
      if (isInstrumented)
        ScalafmtConfig.default.copy(runner = ScalafmtRunner.sbt)
      else
        ScalafmtConfig.default

    Scalafmt.format(code, style = config) match {
      case Formatted.Success(formattedCode) =>
        println("format success"); Some(formattedCode)
      case Formatted.Failure(e) => e.printStackTrace(); None
    }
  }

  def receive = {
    case FormatRequest(code, isInstrumented) => {
      println("format in")
      sender ! FormatResponse(format(code, isInstrumented))
    }
    case SbtTask(id, inputs, progressActor) => {
      val scalaTargetType = inputs.target.targetType

      val inputs0 =
        if (inputs.isInstrumented)
          inputs.copy(code = instrumentation.Instrument(inputs.code))
        else
          inputs

      def eval(command: String) =
        sbt.eval(command,
                 inputs0,
                 processSbtOutput(
                   inputs.isInstrumented,
                   progressActor,
                   id,
                   sender
                 ))

      def timeout(duration: FiniteDuration): Unit = {
        println(s"== restarting sbt $id ==")
        progressActor !
          PasteProgress(
            id = id,
            output = s"Task timed out after $duration",
            done = true,
            compilationInfos = Nil,
            instrumentations = Nil,
            timeout = true
          )

        sbt.close()
        sbt = new Sbt()
      }

      println(s"== updating $id ==")

      val sbtReloadTime = 40.seconds
      if (sbt.needsReload(inputs0)) {
        withTimeout(sbtReloadTime)(eval("compile"))(timeout(sbtReloadTime))
      }

      println(s"== running $id ==")

      withTimeout(runTimeout)({
        scalaTargetType match {
          case JVM | Dotty | Native => eval("run")
          case JS => eval("fastOptJs")
        }
      })(timeout(runTimeout))

      println(s"== done  $id ==")
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
      isInstrumented: Boolean,
      progressActor: ActorRef,
      id: Long,
      pasteActor: ActorRef): (String, Boolean) => Unit = { (line, done) =>
    {
      val problems = extractProblems(line, isInstrumented)
      val instrumentations = extract[api.Instrumentation](line)

      val output =
        if (problems.isEmpty && instrumentations.isEmpty && !done) line + nl
        else ""

      val progress = PasteProgress(
        id = id,
        output = output,
        done = done,
        compilationInfos = problems.getOrElse(Nil),
        instrumentations = instrumentations.getOrElse(Nil),
        timeout = false
      )

      progressActor ! progress
      pasteActor ! progress
    }
  }

  private def extractProblems(
      line: String,
      isInstrumented: Boolean): Option[List[api.Problem]] = {
    val sbtProblems = extract[sbtapi.Problem](line)

    def toApi(p: sbtapi.Problem): api.Problem = {
      val severity = p.severity match {
        case sbtapi.Info => api.Info
        case sbtapi.Warning => api.Warning
        case sbtapi.Error => api.Error
      }
      val lineOffset =
        if (isInstrumented) 2
        else 0

      api.Problem(severity, p.line.map(_ + lineOffset), p.message)
    }

    sbtProblems.map(_.map(toApi))
  }

  private def extract[T: Reader](line: String): Option[List[T]] = {
    try { Some(uread[List[T]](line)) } catch {
      case NonFatal(e) => None
    }
  }
}
