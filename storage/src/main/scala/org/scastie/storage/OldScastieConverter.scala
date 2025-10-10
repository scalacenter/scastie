package org.scastie.storage

import org.scastie.api._

object OldScastieConverter {

  private def convertLine(line: String): Converter => Converter = { converter =>
    val sv = "scalaVersion := \""

    if (line.startsWith(sv)) {
      converter.copy(scalaVersion = Some(line.drop(sv.length).dropRight(1)))
    } else {
      line match {
        case "com.felixmulder.dotty.plugin.DottyPlugin.projectSettings" =>
          converter.setTargetType(ScalaTargetType.Scala3)

        case """scalaOrganization in ThisBuild := "org.typelevel"""" =>
          converter.setTargetType(ScalaTargetType.Typelevel)

        case "coursier.CoursierPlugin.projectSettings" => converter

        case _ => converter.appendSbt(line)
      }
    }
  }

  def convertOldOutput(content: String): List[SnippetProgress] = {
    content
      .split("\n")
      .map(line =>
        SnippetProgress.default.copy(
          userOutput = Some(ProcessOutput(line, ProcessOutputType.StdOut, None)),
          isDone = true
        )
      )
      .toList
  }

  def convertOldInput(content: String): BaseInputs = {
    val blockStart = "/***"
    val blockEnd = "*/"

    val blockStartPos = content.indexOf(blockStart)
    val blockEndPos = content.indexOf(blockEnd)

    if (blockStartPos != -1 && blockEndPos != -1 && blockEndPos > blockStartPos) {
      val start = blockStartPos + blockStart.length

      val sbtConfig = content.slice(start, start + blockEndPos - start)
      val code = content.drop(blockEndPos + blockEnd.length).trim

      val converterFn = sbtConfig.split("\n").foldLeft(Converter.nil) { case (converter, line) =>
        convertLine(line)(converter)
      }

      converterFn(SbtInputs.default).copyBaseInput(code = code)
    } else {
      SbtInputs.default.copy(code = content.trim)
    }
  }

  private object Converter {

    def nil: Converter = Converter(
      scalaVersion = None,
      targetType = None,
      sbtExtra = ""
    )

  }

  private case class Converter(
      scalaVersion: Option[String],
      targetType: Option[ScalaTargetType],
      sbtExtra: String
  ) {
    def appendSbt(in: String): Converter = copy(sbtExtra = sbtExtra + "\n" + in)

    def setTargetType(targetType0: ScalaTargetType): Converter = copy(targetType = Some(targetType0))

    def apply(inputs: BaseInputs): BaseInputs = {
      val scalaTarget = targetType match {
        case Some(ScalaTargetType.Scala3) => Scala3.default

        case Some(ScalaTargetType.Typelevel) => scalaVersion
            .map(sv => Typelevel(sv))
            .getOrElse(
              Typelevel.default
            )

        case _ => scalaVersion
            .map(sv => Scala2(sv))
            .getOrElse(
              Scala2.default
            )
      }

      inputs match {
        case sbtInputs: SbtInputs => sbtInputs.copy(
            target = scalaTarget,
            sbtConfigExtra = sbtExtra.trim,
            isWorksheetMode = false
          )
        case _ => inputs.copyBaseInput(
            isWorksheetMode = false
          )
      }

    }

  }

}
