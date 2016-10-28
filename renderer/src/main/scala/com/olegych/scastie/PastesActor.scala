package com.olegych.scastie

import api.ScalaTargetType
import PastesActor._

import akka.actor._
import akka.event.LoggingReceive
import akka.routing.FromConfig

import scalaz.Scalaz._

/**
  */
case class PastesActor(pastesContainer: PastesContainer, progressActor: ActorRef)
  extends Actor with ActorLogging {
  private val failures = context.actorOf(Props[FailuresActor], "failures")
  private val renderer = createRenderer(context, failures)
  private var rendererBySettings = Map[String, ActorRef]()

  def receive = LoggingReceive {
    case AddPaste(content, sbtConfig, scalaTargetType, uid) =>
      val id = nextPasteId
      val paste = Paste(
        id = id,
        content = Some(content),
        sbtConfig = Some(sbtConfig),
        scalaTargetType = Some(scalaTargetType),
        output = Seq(),
        uid = Some(uid),
        renderedContent = None
      )

      writePaste(paste)
      rendererBySettings.getOrElse(sbtConfig, renderer) ! paste
      sender ! paste
    case GetPaste(id) =>
      sender ! readPaste(id)
    case DeletePaste(id, uid) =>
      sender ! deletePaste(id, uid)

    case progress: PasteProgress =>
      progressActor ! progress

    case paste: Paste =>
      paste.sbtConfig.foreach(c => rendererBySettings += (c -> sender()))
      writePaste(paste)
  }

  def writePaste(paste: Paste) {
    val pasteDir = pastesContainer.paste(paste.id)
    val oldPaste = readPaste(paste.id)

    pasteDir.pasteFile.write(paste.content)
    pasteDir.sxrSource.write(paste.renderedContent)

    println("wrote" + paste.sbtConfig)
    pasteDir.sbtConfigFile.write(paste.sbtConfig)

    pasteDir.uidFile.write(paste.uid)
    pasteDir.outputFile.write(Some(paste.output.mkString(System.lineSeparator)), truncate = false)
  }

  def readPaste(id: Long) = {
    def readScalaTargetType(v: String) = v match {
      case "JVM"    => ScalaTargetType.JVM
      case "Dotty"  => ScalaTargetType.Dotty
      case "JS"     => ScalaTargetType.JS
      case "Native" => ScalaTargetType.Native
    }

    val paste = pastesContainer.paste(id)
    if (paste.pasteFile.exists) {
      Paste(
        id = id,
        content = paste.pasteFile.read,
        sbtConfig = paste.sbtConfigFile.read,
        scalaTargetType = paste.scalaTargetTypeFile.read.map(s => readScalaTargetType(s.trim)),
        output = paste.outputFile.read.map(_.split(System.lineSeparator).toList).getOrElse(Seq()),
        uid = paste.uidFile.read,
        renderedContent = paste.sxrSource.read
      )
    } else {
      Paste(
        id = id,
        content = None,
        sbtConfig = None,
        scalaTargetType = None,
        output = Seq("Not found"),
        uid = None,
        renderedContent = None
      )
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

  case class AddPaste(content: String, sbtConfig: String, scalaTargetType: ScalaTargetType, uid: String) extends PasteMessage

  case class GetPaste(id: Long) extends PasteMessage

  case class DeletePaste(id: Long, uid: String) extends PasteMessage

  case class Paste(
    id: Long,
    content: Option[String],
    sbtConfig: Option[String],
    scalaTargetType: Option[ScalaTargetType],
    output: Seq[String],
    uid: Option[String], 
    renderedContent: Option[String],
    problems: List[api.Problem] = List(),
    instrumentations: List[api.Instrumentation] = List()
  ) extends PasteMessage

  case class PasteProgress(
    id: Long,
    done: Boolean = false,
    output: String = "",
    compilationInfos: List[api.Problem] = Nil,
    instrumentations: List[api.Instrumentation] = Nil
  )
}
