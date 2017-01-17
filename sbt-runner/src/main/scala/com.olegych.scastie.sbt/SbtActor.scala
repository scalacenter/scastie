package com.olegych.scastie
package sbt

import api._
import ScalaTargetType._

import upickle.default.{read => uread, Reader}

import akka.actor.{Actor, ActorRef, ActorLogging}

import scala.concurrent.duration.FiniteDuration
import scala.util.control.{NonFatal, NoStackTrace}

import System.{lineSeparator => nl}

class SbtActor(timeout: FiniteDuration) extends Actor with ActorLogging {
  private var sbt = new Sbt()

  def receive = {
    case task @ SbtTask(id, inputs, progressActor) => {
      log.info("Got: {}", task)

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

      applyTimeout(id, progressActor) {
        scalaTargetType match {
          case JVM | Dotty | Native => eval("run")
          case JS                   => eval("fastOptJs")
        }
      }
    }
    case x => log.warning("Received unknown message: {}", x)
  }

  override def postStop(): Unit = {
    sbt.close()
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

  private val timeoutKiller = createKiller(timeout)

  private def applyTimeout(pasteId: Long, progressActor: ActorRef)(
      block: => Unit) {
    timeoutKiller { case _ => block }((pasteId, progressActor))
  }

  private def createKiller(
      timeout: FiniteDuration): (Actor.Receive) => Actor.Receive = {
    TimeoutActor("killer", timeout, message => {
      sbt.close()
      sbt = new Sbt()

      message match {
        case (pasteId: Long, progressActor: ActorRef) =>
          progressActor ! PasteProgress(
            id = pasteId,
            output = s"Task timed out after $timeout",
            done = true,
            compilationInfos = List(),
            instrumentations = List(),
            timeout = true
          )
        case _ =>
        // log.info("unknown message {}", message)
      }
    })
  }
}

object FatalFailure extends Throwable with NoStackTrace
