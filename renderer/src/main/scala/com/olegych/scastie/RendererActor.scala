package com.olegych.scastie

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import com.olegych.scastie.FailuresActor.{FatalFailure, AddFailure}
import com.olegych.scastie.PastesActor.Paste

import scala.concurrent._
import scala.concurrent.duration._
import scalaz.Scalaz._

import upickle.default.{read => uread}

/**
  */
case class RendererActor(failures: ActorRef) extends Actor with ActorLogging {
  private val killer = createKiller(2.minutes)
  private val runKiller = createKiller(30.seconds)

  private def applyRunKiller(paste: Paste)(block: => Unit) {
    runKiller { case _ => block } apply paste
  }

  private def createKiller(timeout: FiniteDuration): (Actor.Receive) => Actor.Receive = {
    TimeoutActor(timeout, message => {
      message match {
        case paste: Paste => sender ! paste
          .copy(output = Seq(s"Killed because of timeout $timeout"), content = None)
        case _ => log.info("unknown message {}", message)
      }
      preRestart(FatalFailure, Some(message))
    })
  }
  
  private def generateId: String = scala.util.Random.alphanumeric.take(10).mkString
  private val sbtDir = PastesContainer(new java.io.File(System.getProperty("java.io.tmpdir"))).renderer(generateId)
  private var sbt: Option[Sbt] = None
  private var settings = ""

  override def preStart() {
    settings = ""
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    super.preRestart(reason, message)
    message.collect {
      case message@Paste(_, content, _, _, _, _, _) => failures ! AddFailure(reason, message, sender, content)
    }
  }

  override def postStop() {
    log.info("stopping sbt")
    sbt.foreach(_.close())
  }

  def receive = LoggingReceive {
    killer { case paste@Paste(_, Some(content), _, _, _, _, _) =>  {
      if(sbt.isEmpty) {
        sbt = blocking {Option(RendererTemplate.create(sbtDir.root, generateId))}    
      }

      sbt.foreach { sbt =>
        sbtDir.pasteFile.write(Option(content))

        if (settings =/= paste.settings) {
          settings = paste.settings
          sbt.process("reload", (line, _) => {
            println(line)
            sender ! paste.copy(content = sbtDir.pasteFile.read, output = line +: paste.output)
          })
        } else {
          sender ! paste.copy(content = sbtDir.pasteFile.read, output = Seq())
        }

        sbtDir.sxrSource.delete()
        applyRunKiller(paste) {
          sbt.process(";compile ;run-all", (line, done) => {

            val sbtProblems =
              try{ uread[List[sbtapi.Problem]](line) }
              catch { case scala.util.control.NonFatal(e) => List()}

            def toApi(p: sbtapi.Problem): api.Problem = {
              val severity = p.severity match {
                case sbtapi.Info    => api.Info
                case sbtapi.Warning => api.Warning
                case sbtapi.Error   => api.Error
              }
              api.Problem(severity, p.offset, p.message)
            }

            val problems = sbtProblems.map(toApi)

            val instrumentations =
              try{ uread[List[api.Instrumentation]](line) }
              catch { case scala.util.control.NonFatal(e) => List()}

            sender ! paste.copy(output = line +: paste.output, problems = problems, instrumentations = instrumentations)
          })
        }
      }
    }}
  }

  def cleanSource(sxrSource: String): String = {
    sxrSource.replaceFirst("^(?mis).*<body>", "").replaceFirst("(?mis)</body>\\s*</html>$", "")
  }
}
