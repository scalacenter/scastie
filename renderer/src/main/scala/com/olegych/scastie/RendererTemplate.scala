package com.olegych.scastie

import java.io.File

import org.slf4j.LoggerFactory

import scalax.file.Path

/**
  */
object RendererTemplate {
  private val log = LoggerFactory.getLogger(getClass)

  val templateRoot = new File("renderer-template")
  val defaultUniqueId = "$uniqueId$"

  withSbt(templateRoot, defaultUniqueId)(_.process("update"))

  def create(dir: File, uniqueId: String) = this.synchronized {
    log.info("creating paste sbt project")
    Path(templateRoot).copyTo(Path(dir))
    val buildSbt = Path(dir) / "build.sbt"
    val content = buildSbt.string.replaceAllLiterally(defaultUniqueId, uniqueId)
    buildSbt.truncate(0)
    buildSbt.write(content)
    val pluginsSbt = Path(dir) / "project" / "plugins.sbt"
    pluginsSbt.append("\n\nskip := true\n")
    log.info("starting sbt")
    new Sbt(dir, clearOnExit = true, uniqueId)
  }

  def withSbt(root: File, uniqueId: String = Sbt.defaultUniqueId)
             (processor: (Sbt) => Seq[String]) = {
    val out = for {
      sbt <- resource.managed(new Sbt(root, clearOnExit = false, uniqueId = uniqueId))
    } yield {
      processor(sbt)
    }
    out.either match {
      case Left(errors) => errors.foreach(log.error("Exception creating template", _)); throw errors.head
      case Right(out) => out
    }
  }
}
