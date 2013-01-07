package com.olegych.scastie

import java.io.File
import akka.event.LoggingAdapter

/**
  */
object RendererTemplate {
  def create(dir: File, log: LoggingAdapter, uniqueId: String) = this.synchronized {
    log.info("creating paste sbt project")
    withSbt("renderer-template/src/main/g8", log, "$uniqueId$")(_.process("compile"))
    withSbt("renderer-template", log) { sbt =>
      val path = dir.getAbsolutePath.replaceAll("\\\\", "/")
      sbt.process( s"""set G8Keys.outputPath in G8Keys.g8 in Compile := file("$path") """) ++
          sbt.process(
            s"""set G8Keys.properties in G8Keys.g8 in Compile ~= _ + ("uniqueId", "$uniqueId") """) ++
          sbt.process( """g8""")

    }
    log.info("starting sbt")
    new Sbt(dir, log, clearOnExit = true, uniqueId)
  }

  def withSbt(root: String, log: LoggingAdapter, uniqueId: String = Sbt.defaultUniqueId)
             (processor: (Sbt) => Seq[String]) = {
    val out = for {
      sbt <- resource.managed(new Sbt(new File(root), log, clearOnExit = false, uniqueId = uniqueId))
    } yield {
      processor(sbt)
    }
    out.either match {
      case Left(errors) => errors.foreach(log.error(_, "Exception creating template")); throw errors.head
      case Right(out) => out
    }
  }
}
