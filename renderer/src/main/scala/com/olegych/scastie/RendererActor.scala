package com.olegych.scastie

import akka.actor.{ActorLogging, Actor}
import akka.event.LoggingReceive
import java.io.File
import com.olegych.scastie.PastesActor.Paste

/**
  */
class RendererActor() extends Actor with ActorLogging {
  def generateId: String = util.Random.alphanumeric.take(10).mkString

  val sbtDir = PastesContainer(new File(System.getProperty("java.io.tmpdir"))).renderer(generateId)

  var sbt: Option[Sbt] = None

  override def preStart() {
    sbt = Option(new RendererTemplate(sbtDir.root, log, generateId).create)
  }

  var lastFailedMessage: Option[Any] = None

  override def preRestart(reason: Throwable, message: Option[Any]) {
    super.preRestart(reason, message)
    if (message != lastFailedMessage) {
      lastFailedMessage = message
      message.foreach(self forward _)
    }
  }

  override def postStop() {
    log.info("stopping sbt")
    sbt.foreach(_.close())
  }

  def receive = LoggingReceive {
    case paste@Paste(id, Some(content), _) => {
      sbt map { sbt =>
        import scalax.io.Resource._
        def sendPasteFile(result: String) {
          sender !
              paste.copy(content = Option(fromFile(sbtDir.pasteFile).string), output = Option(result))
        }
        sbtDir.writeFile(sbtDir.pasteFile, Option(content))
        val reloadResult = sbt.resultAsString(sbt.process("reload"))
        sendPasteFile(reloadResult)
        sbt.process("compile") match {
          case sbt.Success(compileResult) =>
            val sxrSource = Option(cleanSource(fromFile(sbtDir.sxrSource).string))
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

  def cleanSource(sxrSource: String): String = {
    sxrSource.replaceFirst("^(?mis).*<body>", "").replaceFirst("(?mis)</body>\\s*</html>$", "")
  }
}


