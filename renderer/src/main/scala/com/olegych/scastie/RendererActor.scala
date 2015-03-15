package com.olegych.scastie

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import com.olegych.scastie.FailuresActor.{AddFailure, FatalFailure}
import com.olegych.scastie.PastesActor.Paste

import scala.concurrent._
import scala.concurrent.duration._
import scalaz.Scalaz._

/**
  */
case class RendererActor(failures: ActorRef) extends Actor with ActorLogging {
  private val killer = createKiller(2.minutes)
  private val runKiller = createKiller(30.seconds)

  private def applyRunKiller(paste: Paste)(block: => Unit) {
    runKiller { case _ => block} apply paste
  }

  private def createKiller(timeout: FiniteDuration): (Actor.Receive) => Actor.Receive = {
    TimeoutActor(timeout, message => {
      message match {
        case paste: Paste => sender ! paste
          .copy(output = Some(s"Killed because of timeout $timeout"), content = None)
        case _ => log.info("unknown message {}", message)
      }
      preRestart(FatalFailure, Some(message))
    })
  }

  private def generateId: String = scala.util.Random.alphanumeric.take(10).mkString

  private val sbtDir = PastesContainer(new java.io.File(System.getProperty("java.io.tmpdir"))).renderer(generateId)

  private var sbt: Option[Sbt] = None
  private var settings = ""
  private var reloadResult = ""

  override def preStart() {
    sbt = blocking {Option(RendererTemplate.create(sbtDir.root, log, generateId))}
    settings = ""
    reloadResult = ""
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    super.preRestart(reason, message)
    message.collect {
      case message@Paste(_, content, _, _, _) => failures ! AddFailure(reason, message, sender, content)
    }
  }

  override def postStop() {
    log.info("stopping sbt")
    sbt.foreach(_.close())
  }

  def receive = LoggingReceive {
    killer { case paste@Paste(_, Some(content), _, _, _) => sbt foreach { sbt =>
      def sendPasteFile(result: String) {
        sender ! paste.copy(content = sbtDir.pasteFile.read, output = Option(result))
      }
      sbtDir.pasteFile.write(Option(content))
      val settings = paste.settings
      if (this.settings =/= settings) {
        val reloadResult = sbt.resultAsString(sbt.process(s"reload"))
        this.reloadResult = reloadResult
        this.settings = settings
      } else {
        sendPasteFile("Reused last reload result")
      }
      sendPasteFile(reloadResult)
      sbtDir.sxrSource.delete()
      sbt.process("compile") match {
        case sbt.Success(compileResult) =>
          sender ! paste.copy(
            content = sbtDir.pasteFile.read
            , renderedContent = sbtDir.sxrSource.read.map(cleanSource)
            , output = Option(compileResult + "\nNow running..."))
          applyRunKiller(paste) {
            sbt.process("run-all") match {
              case sbt.Success(runResult) =>
                sender ! paste.copy(output = Option(runResult))
              case errorResult =>
                sender ! paste.copy(output = Option(sbt.resultAsString(errorResult)))
            }
          }
        case errorResult =>
          sendPasteFile(sbt.resultAsString(errorResult))
      }
    }
    }
  }

  def cleanSource(sxrSource: String): String = {
    sxrSource.replaceFirst("^(?mis).*<body>", "").replaceFirst("(?mis)</body>\\s*</html>$", "")
  }
}
