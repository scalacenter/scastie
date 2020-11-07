package com.olegych.scastie
package sbt

import akka.actor.Actor
import com.olegych.scastie.api.{FormatRequest, FormatResponse, ScalaTarget}
import org.scalafmt.config.ScalafmtRunner.Dialect
import org.scalafmt.config.{ScalafmtConfig, ScalafmtRunner}
import org.scalafmt.{Formatted, Scalafmt}
import org.slf4j.LoggerFactory

class FormatActor() extends Actor {
  private val log = LoggerFactory.getLogger(getClass)

  private def format(code: String, isWorksheetMode: Boolean, scalaTarget: ScalaTarget): Either[String, String] = {
    log.info(s"format (isWorksheetMode: $isWorksheetMode)")
    log.info(code)

    val config = scalaTarget match {
      case scalaTarget if isWorksheetMode && scalaTarget.hasWorksheetMode =>
        ScalafmtConfig.default.copy(runner = ScalafmtRunner.sbt)
      case dotty: ScalaTarget.Dotty =>
        ScalafmtConfig.default.withDialect(scala.meta.dialects.Dotty)
      case _ =>
        ScalafmtConfig.default
    }

    Scalafmt.format(code, style = config) match {
      case Formatted.Success(formattedCode) => Right(formattedCode)
      case Formatted.Failure(failure) =>
        Left(failure.toString)
    }
  }

  override def receive: Receive = {
    case FormatRequest(code, isWorksheetMode, scalaTarget) =>
      sender() ! FormatResponse(format(code, isWorksheetMode, scalaTarget))
  }
}
