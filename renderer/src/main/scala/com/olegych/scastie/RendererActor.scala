package com.olegych.scastie

import akka.actor.{ActorLogging, Actor}
import akka.event.LoggingReceive
import java.io.File
import com.olegych.scastie.PastesActor.Paste
import com.olegych.scastie.FailuresActor.{FatalFailure, AddFailure}
import concurrent.duration._

/**
  */
class RendererActor extends Actor with ActorLogging {
  val failures = context.actorFor("../../failures")

  val killer = TimeoutActor(2 minutes, message => {
    message match {
      case paste: Paste => sender ! paste.copy(output = Some("Killed because of timeout!"), content = None)
      case _ => log.info("unknown message {}", message)
    }
    preRestart(FatalFailure, Some(message))
  })

  def generateId: String = util.Random.alphanumeric.take(10).mkString

  val sbtDir = PastesContainer(new File(System.getProperty("java.io.tmpdir"))).renderer(generateId)

  var sbt: Option[Sbt] = None

  override def preStart() {
    sbt = Option(new RendererTemplate(sbtDir.root, log, generateId).create)
  }

  override def preRestart(reason: Throwable, message: Option[Any]) {
    super.preRestart(reason, message)
    message.collect {
      case message@Paste(_, content, _, _) => failures ! AddFailure(reason, message, sender, content)
    }
  }

  override def postStop() {
    log.info("stopping sbt")
    sbt.foreach(_.close())
  }

  def receive = LoggingReceive {
    killer {
      case paste@Paste(_, Some(content), _, _) => {
        sbt map { sbt =>
          def sendPasteFile(result: String) {
            sender !
                paste.copy(content = sbtDir.pasteFile.read, output = Option(result))
          }
          sbtDir.pasteFile.write(Option(content))
          val reloadResult = sbt.resultAsString(sbt.process("reload"))
          sendPasteFile(reloadResult)
          sbtDir.sxrSource.delete()
          sbt.process("compile") match {
            case sbt.Success(compileResult) =>
              val sxrSource = sbtDir.sxrSource.read.map(cleanSource)
              sender ! paste.copy(content = sxrSource, output = Option(compileResult + "\nNow running..."))
              sbt.process("run-all") match {
                case sbt.Success(runResult) =>
                  sender ! paste.copy(content = sxrSource, output = Option(runResult))
                case errorResult =>
                  sender ! paste.copy(content = sxrSource, output = Option(sbt.resultAsString(errorResult)))
              }
            case sbt.NotTopLevelExpression(compileResult) =>
              sendPasteFile(compileResult + "\nAdding top level object and recompiling...")
              val fixedContent = s"object Main extends App {\n$content\n}"
              self forward paste.copy(content = Option(fixedContent))
            case errorResult =>
              sendPasteFile(sbt.resultAsString(errorResult))
          }
        }
      }
    }
  }

  def cleanSource(sxrSource: String): String = {
    sxrSource.replaceFirst("^(?mis).*<body>", "").replaceFirst("(?mis)</body>\\s*</html>$", "")
  }
}


