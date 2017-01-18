package com.olegych.scastie
package sbt

import api._
import ScalaTargetType._

import upickle.default.{read => uread, Reader}

import akka.actor.{Actor, ActorRef}

import scala.concurrent._
import scala.concurrent.duration._
import java.util.concurrent.{TimeoutException, Callable, FutureTask, TimeUnit}

import scala.util.control.NonFatal
import System.{lineSeparator => nl}

class SbtActor(runTimeout: FiniteDuration) extends Actor {
  private var sbt = new Sbt()

  def receive = {
    case SbtTask(id, inputs, progressActor) => {
      val scalaTargetType = inputs.target.targetType

      val inputs0 =
        inputs.copy(code = instrumentation.Instrument(inputs.code))

      def eval(command: String) =
        sbt.eval(command,
                 inputs0,
                 processSbtOutput(
                   progressActor,
                   id,
                   sender
                 ))

      def timeout(duration: FiniteDuration): Unit = {
        println(s"== restarting sbt $id ==")

        sbt.close()
        sbt = new Sbt()

        progressActor ! 
          PasteProgress(
            id = id,
            output = s"Task timed out after $duration",
            done = true,
            compilationInfos = Nil,
            instrumentations = Nil,
            timeout = true
          )
      }

      println(s"== updating $id ==")

      val sbtReloadTime = 40.seconds
      if(sbt.needsReload(inputs0)) {
        withTimeout(sbtReloadTime)(eval("compile"))(timeout(sbtReloadTime))
      }

      println(s"== running $id ==")

      withTimeout(runTimeout)({
        scalaTargetType match {
          case JVM | Dotty | Native => eval("run")
          case JS                   => eval("fastOptJs")
        }
      })(timeout(runTimeout))

      println(s"== done  $id ==")
    }
  }

  private def withTimeout(timeout: Duration)(block: ⇒ Unit)(onTimeout: => Unit): Unit = {
    val task = new FutureTask(new Callable[Unit]() { def call = block })
    val thread = new Thread(task)
    try {
      thread.start()
      task.get(timeout.toMillis, TimeUnit.MILLISECONDS)
    } catch {
      case e: TimeoutException ⇒ onTimeout
    } finally {
      if(thread.isAlive) thread.stop()
    }
  }

  private def processSbtOutput(
      progressActor: ActorRef,
      id: Long,
      pasteActor: ActorRef): (String, Boolean) => Unit = { (line, done) =>
    {
      val problems = extractProblems(line)
      val instrumentations = extract[api.Instrumentation](line)

      val output =
        if(problems.isEmpty && instrumentations.isEmpty && !done) line + nl
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

  private def extractProblems(line: String): Option[List[api.Problem]] = {
    val sbtProblems = extract[sbtapi.Problem](line)

    def toApi(p: sbtapi.Problem): api.Problem = {
      val severity = p.severity match {
        case sbtapi.Info    => api.Info
        case sbtapi.Warning => api.Warning
        case sbtapi.Error   => api.Error
      }
      api.Problem(severity, p.line, p.message)
    }

    sbtProblems.map(_.map(toApi))
  }

  private def extract[T: Reader](line: String): Option[List[T]] = {
    try { Some(uread[List[T]](line)) } catch {
      case NonFatal(e) => None
    }
  }
}
