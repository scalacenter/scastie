package com.olegych.scastie
package sbt

import akka.actor.Actor
import com.olegych.scastie.api.FormatRequest
import com.olegych.scastie.api.FormatResponse
import com.olegych.scastie.api.ScalaTarget
import org.scalafmt.Formatted
import org.scalafmt.Scalafmt
import org.scalafmt.config.ScalafmtConfig
import org.scalafmt.config.ScalafmtRunner
import org.scalafmt.config.NamedDialect
import org.slf4j.LoggerFactory

object FormatActor {
  private[sbt] def format(code: String, isWorksheetMode: Boolean, scalaTarget: ScalaTarget): Either[String, String] = {
    val config: ScalafmtConfig = {
      val dialect =
        if (scalaTarget.scalaVersion.startsWith("2.12")) NamedDialect.scala212
        else if (scalaTarget.scalaVersion.startsWith("2.13")) NamedDialect.scala213
        else if (scalaTarget.scalaVersion.startsWith("3")) NamedDialect.scala3
        else NamedDialect.scala213

      val runner = {
        if (isWorksheetMode && scalaTarget.hasWorksheetMode)
          ScalafmtRunner.sbt
        else
          ScalafmtRunner.default
      }.withDialect(dialect)

      ScalafmtConfig.default.copy(runner = runner)
    }

    Scalafmt.format(code, style = config) match {
      case Formatted.Success(formattedCode) => Right(formattedCode)
      case Formatted.Failure(failure)       => Left(failure.toString)
    }
  }
}

class FormatActor() extends Actor {
  import FormatActor._
  private val log = LoggerFactory.getLogger(getClass)

  override def receive: Receive = {
    case FormatRequest(code, isWorksheetMode, scalaTarget) =>
      log.info(s"format (isWorksheetMode: $isWorksheetMode)")
      log.info(code)

      sender() ! FormatResponse(format(code, isWorksheetMode, scalaTarget))
  }
}
