package com.olegych.scastie

import akka.actor.{Props, Actor, ActorLogging}
import akka.event.LoggingReceive
import com.olegych.scastie.PastesActor.GetPaste
import com.olegych.scastie.PastesActor.AddPaste
import com.olegych.scastie.PastesActor.Paste

/**
  */
class PastesActor(pastesContainer: PastesContainer) extends Actor with ActorLogging {
  val renderer = context.actorOf(Props(new RendererActor(pastesContainer)))

  protected def receive = LoggingReceive {
    case AddPaste(content) =>
      val id = nextPasteId
      val paste = Paste(id = id, content = content, output = "Processing")
      renderer ! paste
      sender ! paste
      writePaste(paste)
    case GetPaste(id) =>
      sender ! readPaste(id)
    case paste@Paste(id, content, output) =>
      writePaste(paste)
  }

  def writePaste(paste: Paste) {
    if (paste.content.size > 0) {
      val pasteDir = pastesContainer.paste(paste.id)
      import scalax.io.Resource._
      val pasteFile = fromFile(pasteDir.pasteFile)
      pasteFile.truncate(0)
      pasteFile.write(paste.content)
      val outputFile = fromFile(pasteDir.outputFile)
      outputFile.truncate(0)
      outputFile.write(paste.output)
    }
  }

  def readPaste(id: Long) = {
    val paste = pastesContainer.paste(id)
    if (paste.pasteFile.exists()) {
      import scalax.io.Resource._
      Paste(id = id, content = fromFile(paste.pasteFile).slurpString(),
        output = fromFile(paste.outputFile).slurpString())
    } else {
      Paste(id = id, content = "", output = "")
    }
  }

  def nextPasteId = pastesContainer.lastPasteId.getAndIncrement
}

object PastesActor {

  sealed trait PasteMessage

  case class AddPaste(content: String) extends PasteMessage

  case class GetPaste(id: Long) extends PasteMessage

  case class Paste(id: Long, content: String, output: String) extends PasteMessage

}