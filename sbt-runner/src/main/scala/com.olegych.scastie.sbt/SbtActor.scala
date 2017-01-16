package com.olegych.scastie
package sbt

import api._

import upickle.default.{read => uread}

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive

import scala.concurrent.duration._
import scala.util.control.{NonFatal, NoStackTrace}

class SbtActor() extends Actor with ActorLogging {
  private val sbt = new Sbt()

  def receive = LoggingReceive {
    compilationKiller {
      case (id: Long, inputs: Inputs, progressActor: ActorRef) => {

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

        applyRunKiller(id, progressActor) {
          if (scalaTargetType == ScalaTargetType.JVM ||
              scalaTargetType == ScalaTargetType.Dotty) {
            eval("run")
          } else if (scalaTargetType == ScalaTargetType.JS) {
            eval("fastOptJs")
          } else if (scalaTargetType == ScalaTargetType.Native) {
            eval("run")
          }
        }
      }
    }
  }

  override def postStop() {
    log.info("stopping sbt")
    sbt.close()
  }

  private def processSbtOutput(
      progressActor: ActorRef,
      id: Long,
      pasteActor: ActorRef): (String, Boolean) => Unit = { (line, done) =>
    {
      val progress = PasteProgress(
        id = id,
        output = line,
        done = done,
        compilationInfos = extractProblems(line),
        instrumentations = extractInstrumentations(line),
        timeout = false
      )

      progressActor ! progress
      pasteActor ! progress
    }
  }

  private def extractProblems(line: String): List[api.Problem] = {
    val sbtProblems =
      try { uread[List[sbtapi.Problem]](line) } catch {
        case NonFatal(e) => List()
      }

    def toApi(p: sbtapi.Problem): api.Problem = {
      val severity = p.severity match {
        case sbtapi.Info    => api.Info
        case sbtapi.Warning => api.Warning
        case sbtapi.Error   => api.Error
      }
      api.Problem(severity, p.line, p.message)
    }

    sbtProblems.map(toApi)
  }

  private def extractInstrumentations(line: String): List[api.Instrumentation] = {
    try { uread[List[api.Instrumentation]](line) } catch {
      case NonFatal(e) => List()
    }
  }

  private val compilationKiller = createKiller("CompilationKiller", 2.minutes)
  private val runKiller         = createKiller("RunKiller", 20.seconds)

  private def applyRunKiller(pasteId: Long, progressActor: ActorRef)(block: => Unit) {
    runKiller{ case _ => block }((pasteId, progressActor))
  }

  private def createKiller(
      actorName: String,
      timeout: FiniteDuration): (Actor.Receive) => Actor.Receive = {
    TimeoutActor(actorName, timeout, message => {
      message match {
        case (pasteId: Long, progressActor: ActorRef) =>
          progressActor ! PasteProgress(
            id = pasteId,
            output = "",
            done = true,
            compilationInfos = List(),
            instrumentations = List(),
            timeout = true
          )
        case _ =>
          log.info("unknown message {}", message)
      }
      preRestart(FatalFailure, Some(message))
    })
  }
}

object FatalFailure extends Throwable with NoStackTrace
