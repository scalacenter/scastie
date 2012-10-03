package com.olegych.scastie

import akka.actor.{Props, Actor, ActorLogging}
import akka.event.LoggingReceive
import com.olegych.scastie.PastesActor.GetPaste
import com.olegych.scastie.PastesActor.AddPaste
import com.olegych.scastie.PastesActor.Paste
import java.io.File

/**
  */
class PastesActor(pastesContainer: PastesContainer) extends Actor with ActorLogging {
  val renderer = context.actorOf(Props(new RendererActor(pastesContainer)))

  protected def receive = LoggingReceive {
    case AddPaste(content) =>
      val id = nextPasteId
      val paste = Paste(id = id, content = Option(content), output = Option("Processing"))
      renderer ! paste
      sender ! paste
      writePaste(paste)
    case GetPaste(id) =>
      sender ! readPaste(id)
    case paste@Paste(id, content, output) =>
      writePaste(paste)
  }

  def writePaste(paste: Paste) {
    val pasteDir = pastesContainer.paste(paste.id)
    pasteDir.writeFile(pasteDir.pasteFile, paste.content)
    pasteDir.writeFile(pasteDir.outputFile, paste.output, truncate = false)
  }

  def readPaste(id: Long) = {
    val paste = pastesContainer.paste(id)
    if (paste.pasteFile.exists()) {
      import scalax.io.Resource._
      Paste(id = id, content = Option(fromFile(paste.pasteFile).slurpString()),
        output = Option(fromFile(paste.outputFile).slurpString()))
    } else {
      Paste(id = id, content = None, output = Option("Not found"))
    }
  }

  def nextPasteId = pastesContainer.lastPasteId.incrementAndGet()
}

object PastesActor {

  sealed trait PasteMessage

  case class AddPaste(content: String) extends PasteMessage

  case class GetPaste(id: Long) extends PasteMessage

  case class Paste(id: Long, content: Option[String], output: Option[String]) extends PasteMessage

}