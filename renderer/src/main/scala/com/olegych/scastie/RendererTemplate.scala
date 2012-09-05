package com.olegych.scastie

import java.io.File

/**
  */
class RendererTemplate(dir: File) {
  def create = {
    val log = for {
      sbt <- resource.managed(new Sbt(new File("renderer-template")))
    } yield {
      val path = dir.getAbsolutePath.replaceAll("\\\\", "/")
      sbt.process(
        """set G8Keys.outputPath in G8Keys.g8 in Compile := file("%s") """.format(path)) +
          sbt.process( """set G8Keys.properties in G8Keys.g8 in Compile := Map(("name", "helloname")) """) +
          sbt.process( """g8""")
    }
    log.either match {
      case Left(Seq(error, _*)) => throw error
      case Right(log) => log
    }
  }
}
