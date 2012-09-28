package com.olegych.scastie

import akka.actor.{ActorLogging, Actor}
import akka.event.LoggingReceive
import java.io.File
import com.olegych.scastie.PastesActor.Paste
import java.util.concurrent.atomic.AtomicLong

/**
  */
class RendererActor(pastesContainer: PastesContainer) extends Actor with ActorLogging {
  def generateId: String = util.Random.alphanumeric.take(10).mkString

  val sbtDir = pastesContainer.renderer(generateId)

  var sbt: Option[Sbt] = None

  override def preStart() {
    sbt = Option(new RendererTemplate(sbtDir.root, log, generateId).create)
  }

  override def postStop() {
    log.info("stopping sbt")
    sbt.foreach(_.close())
  }

  protected def receive = LoggingReceive {
    case paste@Paste(id, Some(content), _) => {
      sbt map { sbt =>
        import scalax.io.Resource._
        def sendPasteFile(result: String) {
          sender !
              paste.copy(content = Option(fromFile(sbtDir.pasteFile).slurpString), output = Option(result))
        }
        sbtDir.writeFile(sbtDir.pasteFile, Option(content))
        sbt.process("compile") match {
          case sbt.Success(compileResult) =>
            val sxrSource = Option(cleanSource(fromFile(sbtDir.sxrSource).slurpString))
            sender !
                paste.copy(content = sxrSource, output = Option(compileResult + "\nNow running"))
            sbt.process("run") match {
              case sbt.Success(runResult) =>
                sender !
                    paste.copy(content = sxrSource, output = Option(compileResult + runResult))
              case errorResult =>
                sender !
                    paste.copy(content = sxrSource,
                      output = Option(compileResult + sbt.resultAsString(errorResult)))
            }
          case sbt.ExpectedClassOrObject(compileResult) =>
            sendPasteFile(compileResult + "\nAdding top level object and recompiling...")
            val fixedContent = "object Main extends App {\n%s\n}".format(content)
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

case class PastesContainer(root: java.io.File) {
  val PasteFormat = "paste(\\d+)".r
  lazy val lastPasteId = new AtomicLong(Option(root.listFiles()).getOrElse(Array()).map(_.getName).collect {
    case PasteFormat(id) => id.toLong
  }.sorted.lastOption.getOrElse(0L))

  def renderer(id: String) = child("renderer" + id)
  def paste(id: Long) = child("paste%20d".format(id).replaceAll(" ", "0"))
  private def child(id: String) = copy(root = new File(root, id))
  def pasteFile = new File(root, "src/main/scala/test.scala")
  def outputFile = new File(root, "src/main/scala/output.txt")
  def sxrSource = new File(root, "target/scala-2.9.2/classes.sxr/test.scala.html")

  def writeFile(file: File, content: Option[String]) {
    content.map { content =>
      import scalax.io.Resource._
      val writer = fromFile(file)
      writer.truncate(0)
      writer.write(content)
    }
  }
}
