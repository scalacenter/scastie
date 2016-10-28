package com.olegych.scastie

import api.ScalaTargetType

import FailuresActor.{FatalFailure, AddFailure}
import PastesActor.{Paste, PasteProgress}

import upickle.default.{read => uread}

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.control.NonFatal

case class RendererActor(failures: ActorRef) extends Actor with ActorLogging {
  private val killer = createKiller(2.minutes)
  private val runKiller = createKiller(30.seconds)

  private def applyRunKiller(paste: Paste)(block: => Unit) {
    runKiller { case _ => block } apply paste
  }

  private def createKiller(timeout: FiniteDuration): (Actor.Receive) => Actor.Receive = {
    TimeoutActor(timeout, message => {
      message match {
        case paste: Paste => 
          sender ! paste.copy(
            output = Seq(s"Killed because of timeout $timeout"), 
            content = None
          )
        case _ => log.info("unknown message {}", message)
      }
      preRestart(FatalFailure, Some(message))
    })
  }
  
  private def generateId: String = scala.util.Random.alphanumeric.take(10).mkString
  private val sbtDir =
    PastesContainer(
      new java.io.File(System.getProperty("java.io.tmpdir"))
    ).renderer(generateId)

  sbtDir.root.deleteOnExit()

  private var sbt: Option[Sbt] = None
  private var sbtConfig = ""

  override def preStart() {
    sbtConfig = ""
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    super.preRestart(reason, message)
    message.collect {
      case message @ Paste(_, content, _, _, _, _, _, _, _) => 
        failures ! AddFailure(reason, message, sender, content)
    }
  }

  override def postStop() {
    log.info("stopping sbt")
    sbt.foreach(_.close())
  }

  private def extractProblems(line: String): List[api.Problem] = {
    val sbtProblems =
      try{ uread[List[sbtapi.Problem]](line) }
      catch { case NonFatal(e) => List()}

    def toApi(p: sbtapi.Problem): api.Problem = {
      val severity = p.severity match {
        case sbtapi.Info    => api.Info
        case sbtapi.Warning => api.Warning
        case sbtapi.Error   => api.Error
      }
      api.Problem(severity, p.offset, p.message)
    }

    sbtProblems.map(toApi)
  }

  private def extractInstrumentations(line: String): List[api.Instrumentation] = {
    try{ uread[List[api.Instrumentation]](line) }
    catch { case NonFatal(e) => List()}
  }

  def processSbtOutput(sender: ActorRef, id: Long,
                       withInstrumentations: Boolean = false): (String, Boolean) => Unit = {
    (line, done) => {
      sender ! PasteProgress(
        id = id,
        output = line,
        done = done,
        compilationInfos = extractProblems(line),
        instrumentations = 
          if(withInstrumentations) extractInstrumentations(line)
          else Nil
      )
    }
  }

  def receive = LoggingReceive { killer { 
    case paste @ Paste(id, Some(content), Some(pasteSbtConfig), Some(scalaTargetType), _, _, _, _, _) =>  {
      if(sbt.isEmpty) {
        sbt = blocking {Option(RendererTemplate.create(sbtDir.root, generateId))}    
      }

      sbt.foreach { sbt =>
        sbtDir.pasteFile.write(Some(content))
        sbtDir.sbtConfigFile.write(Some(pasteSbtConfig))

        if (sbtConfig != pasteSbtConfig) {
          sbtConfig = pasteSbtConfig
          sbt.process("reload", (line, _) => {
            sender ! PasteProgress(id = id, output = line)
          })
        }

        sbtDir.sxrSource.delete()
        applyRunKiller(paste) {
          if(scalaTargetType == ScalaTargetType.JVM || 
            scalaTargetType == ScalaTargetType.Dotty) {
            sbt.process(";compile ;run-all", processSbtOutput(sender, id, withInstrumentations = true))
          }
          else if(scalaTargetType == ScalaTargetType.JS) {
            sbt.process("fast-opt", processSbtOutput(sender, id))
          }
          else if(scalaTargetType == ScalaTargetType.Native) {
            sbt.process("compile", processSbtOutput(sender, id))
          }
        }
      }
    }}
  }
}
