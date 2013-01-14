package com.olegych.scastie

import java.io.File
import akka.event.{NoLogging, LoggingAdapter}
import scalax.file.Path

/**
  */
object RendererTemplate {
  val templateRoot = new File("renderer-template")
  val defaultUniqueId = "$uniqueId$"

  withSbt(templateRoot, NoLogging, defaultUniqueId)(_.process("update"))

  def create(dir: File, log: LoggingAdapter, uniqueId: String) = this.synchronized {
    log.info("creating paste sbt project")
    Path(templateRoot).copyTo(Path(dir))
    val buildSbt = Path(dir) / "build.sbt"
    val content = buildSbt.string.replaceAllLiterally(defaultUniqueId, uniqueId)
    buildSbt.truncate(0)
    buildSbt.write(content)
    val pluginsSbt = Path(dir) / "project" / "plugins.sbt"
    pluginsSbt.append("\n\nskip := true\n")
    log.info("starting sbt")
    new Sbt(dir, log, clearOnExit = true, uniqueId)
  }

  def withSbt(root: File, log: LoggingAdapter, uniqueId: String = Sbt.defaultUniqueId)
             (processor: (Sbt) => Seq[String]) = {
    val out = for {
      sbt <- resource.managed(new Sbt(root, log, clearOnExit = false, uniqueId = uniqueId))
    } yield {
      processor(sbt)
    }
    out.either match {
      case Left(errors) => errors.foreach(log.error(_, "Exception creating template")); throw errors.head
      case Right(out) => out
    }
  }
}
