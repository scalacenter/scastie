package com.olegych.scastie.storage

import com.olegych.scastie.proto._
import com.olegych.scastie.api
import api.{SnippetProgressHelper, InputsHelper}


import System.{lineSeparator => nl}

object OldScastieConverter {
  private def convertLine(line: String): Converter => Converter = { converter =>
    val sv = "scalaVersion := \""

    if (line.startsWith(sv)) {
      converter.copy(scalaVersion = Some(line.drop(sv.length).dropRight(1)))
    } else {
      line match {
        case "com.felixmulder.dotty.plugin.DottyPlugin.projectSettings" =>
          converter.setTargetType(ScalaTargetType.Dotty)

        case """scalaOrganization in ThisBuild := "org.typelevel"""" =>
          converter.setTargetType(ScalaTargetType.TypelevelScala)

        case "coursier.CoursierPlugin.projectSettings" =>
          converter

        case _ =>
          converter.appendSbt(line)
      }
    }
  }

  def convertOldOutput(content: String): List[SnippetProgress] = {
    content
      .split(nl)
      .map(
        line =>
          SnippetProgressHelper.default.copy(
            userOutput = Some(line),
            done = true
          )
      )
      .toList
  }

  def convertOldInput(content: String): Inputs = {
    val blockStart = "/***"
    val blockEnd = "*/"

    val blockStartPos = content.indexOf(blockStart)
    val blockEndPos = content.indexOf(blockEnd)

    if (blockStartPos != -1 && blockEndPos != -1 && blockEndPos > blockStartPos) {
      val start = blockStartPos + blockStart.length

      val sbtConfig = content.slice(start, start + blockEndPos - start)
      val code = content.drop(blockEndPos + blockEnd.length)

      val converterFn =
        sbtConfig.split(nl).foldLeft(Converter.nil) {
          case (converter, line) =>
            convertLine(line)(converter)
        }

      converterFn(InputsHelper.default).copy(code = code.trim)
    } else {
      InputsHelper.default.copy(code = content.trim)
    }
  }

  private object Converter {
    def nil =
      Converter(
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
    def appendSbt(in: String): Converter =
      copy(sbtExtra = sbtExtra + nl + in)

    def setTargetType(targetType0: ScalaTargetType): Converter =
      copy(targetType = Some(targetType0))

    def apply(inputs: Inputs): Inputs = {
      val scalaTarget: ScalaTarget =
        targetType match {
          case Some(ScalaTargetType.Dotty) =>
            api.Dotty.default

          case Some(ScalaTargetType.TypelevelScala) =>
            scalaVersion
              .map(sv => api.TypelevelScala(sv))
              .getOrElse(
                api.TypelevelScala.default
              )


          case _ => 
            scalaVersion
              .map(sv => api.PlainScala(sv))
              .getOrElse(
                api.PlainScala.default
              )
        }

      inputs.copy(
        target = scalaTarget,
        sbtConfigExtra = sbtExtra.trim,
        worksheetMode = false
      )
    }
  }
}
