package com.olegych.scastie

import akka.actor._
import akka.event.LoggingReceive
import akka.routing.FromConfig
import com.olegych.scastie.PastesActor._

import scalaz.Scalaz._

/**
  */
case class PastesActor(pastesContainer: PastesContainer, progressActor: ActorRef)
  extends Actor with ActorLogging {
  private val failures = context.actorOf(Props[FailuresActor], "failures")
  private val renderer = createRenderer(context, failures)
  private var rendererBySettings = Map[String, ActorRef]()

  def receive = LoggingReceive {
    case AddPaste(content, uid) =>
      val id = nextPasteId
      val paste = Paste(id = id, content = Option(content), output = Seq("Processing..."), uid = Some(uid), renderedContent = None)
      writePaste(paste)
      rendererBySettings.getOrElse(paste.settings, renderer) ! paste
      sender ! paste
    case GetPaste(id) =>
      sender ! readPaste(id)
    case DeletePaste(id, uid) =>
      sender ! deletePaste(id, uid)
    case paste: Paste =>
      rendererBySettings += (paste.settings -> sender())
      writePaste(paste)
  }

  def writePaste(paste: Paste) {
    val pasteDir = pastesContainer.paste(paste.id)
    val oldPaste = readPaste(paste.id)
    val contentChanged = oldPaste.content.nonEmpty && (oldPaste.content =/= paste.content || oldPaste.renderedContent =/= paste.renderedContent)
    pasteDir.pasteFile.write(paste.content)
    pasteDir.sxrSource.write(paste.renderedContent)
    pasteDir.uidFile.write(paste.uid)
    progressActor ! PasteProgress(paste.id, contentChanged, paste.output)
    pasteDir.outputFile.write(Some(paste.output.mkString(System.lineSeparator)), truncate = false)
  }

  def readPaste(id: Long) = {
    val paste = pastesContainer.paste(id)
    if (paste.pasteFile.exists) {
      Paste(
        id = id, 
        content = paste.pasteFile.read,
        output = paste.outputFile.read.map(_.split(System.lineSeparator).toList).getOrElse(Seq()),
        uid = paste.uidFile.read,
        renderedContent = paste.sxrSource.read
      )
    } else {
      Paste(id = id, content = None, output = Seq("Not found"), uid = None, renderedContent = None)
    }
  }

  def deletePaste(id: Long, uid: String) = {
    val storedPaste = readPaste(id)
    if (storedPaste.uid == Some(uid)) {
      val paste = pastesContainer.paste(id)
      paste.outputFile.delete()
      paste.pasteFile.delete()
      paste.sxrSource.delete()
      readPaste(id)
    } else {
      storedPaste
    }
  }

  def nextPasteId = pastesContainer.lastPasteId.incrementAndGet()
}

object PastesActor {
  private def createRenderer(context: ActorContext, failures: ActorRef) =
    context.actorOf(Props(RendererActor(failures)).withRouter(FromConfig()), "renderer")

  sealed trait PasteMessage

  case class AddPaste(content: String, uid: String) extends PasteMessage

  case class GetPaste(id: Long) extends PasteMessage

  case class DeletePaste(id: Long, uid: String) extends PasteMessage

  case class Paste(id: Long, content: Option[String], output: Seq[String], uid: Option[String], renderedContent: Option[String])
    extends PasteMessage {
    lazy val settings = Script.blocks(content.orZero.split("\r\n".toCharArray)).flatMap(_.lines).mkString("\n")
  }

  case class PasteProgress(id: Long, contentChanged: Boolean, output: Seq[String])

}
