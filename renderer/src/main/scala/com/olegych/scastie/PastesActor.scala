package com.olegych.scastie

import akka.actor._
import akka.event.LoggingReceive
import akka.routing.FromConfig
import com.olegych.scastie.PastesActor._
import com.olegych.scastie.PastesActor.PasteProgress
import com.olegych.scastie.PastesActor.GetPaste
import com.olegych.scastie.PastesActor.AddPaste
import com.olegych.scastie.PastesActor.Paste

/**
  */
class PastesActor(pastesContainer: PastesContainer, progressActor: ActorRef) extends Actor with ActorLogging {
  val renderer = context.actorOf(Props[RendererActor].withRouter(FromConfig()), "renderer")
  val failures = context.actorOf(Props[FailuresActor], "failures")

  def receive = LoggingReceive {
    case AddPaste(content) =>
      val id = nextPasteId
      val paste = Paste(id = id, content = Option(content), output = Option("Processing..."))
      renderer ! paste
      sender ! paste
      writePaste(paste)
    case GetPaste(id) =>
      sender ! readPaste(id)
    case DeletePaste(id) =>
      deletePaste(id)
      sender ! Paste(id, None, None)
    case paste@Paste(id, content, output) =>
      writePaste(paste)
  }

  def writePaste(paste: Paste) {
    val pasteDir = pastesContainer.paste(paste.id)
    val contentChanged = readPaste(paste.id).content != paste.content
    pasteDir.writeFile(pasteDir.pasteFile, paste.content)
    progressActor ! PasteProgress(paste.id, contentChanged, paste.output.getOrElse(""))
    pasteDir.writeFile(pasteDir.outputFile, paste.output, truncate = false)
  }

  def readPaste(id: Long) = {
    val paste = pastesContainer.paste(id)
    if (paste.pasteFile.exists()) {
      import scalax.io.Resource._
      Paste(id = id, content = Option(fromFile(paste.pasteFile).string),
        output = Option(fromFile(paste.outputFile).string))
    } else {
      Paste(id = id, content = None, output = Option("Not found"))
    }
  }

  def deletePaste(id: Long) {
    val paste = pastesContainer.paste(id)
    paste.outputFile.delete()
    paste.pasteFile.delete()
  }

  def nextPasteId = pastesContainer.lastPasteId.incrementAndGet()
}

object PastesActor {

  sealed trait PasteMessage

  case class AddPaste(content: String) extends PasteMessage

  case class GetPaste(id: Long) extends PasteMessage

  case class DeletePaste(id: Long) extends PasteMessage

  case class Paste(id: Long, content: Option[String], output: Option[String]) extends PasteMessage

  case class PasteProgress(id: Long, contentChanged: Boolean, output: String)

}
