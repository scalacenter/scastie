package com.olegych.scastie
package sbt

import akka.actor.Actor
import com.olegych.scastie.api.{FormatRequest, FormatResponse, ScalaTarget}
import org.scalafmt.config.ScalafmtRunner.Dialect
import org.scalafmt.config.{ScalafmtConfig, ScalafmtRunner}
import org.scalafmt.{Formatted, Scalafmt}
import org.slf4j.LoggerFactory

object FormatActor {
  private[sbt] def format(code: String, isWorksheetMode: Boolean, scalaTarget: ScalaTarget): Either[String, String] = {
    val config: ScalafmtConfig = scalaTarget match {
      case _ if isWorksheetMode && scalaTarget.hasWorksheetMode => ScalafmtConfig.default.copy(runner=ScalafmtRunner.sbt)
      case _: ScalaTarget.Scala3 => ScalafmtConfig.default.withDialect(scala.meta.dialects.Scala3)
      case _ => ScalafmtConfig.default
    }

    Scalafmt.format(code, style = config) match {
      case Formatted.Success(formattedCode) => Right(formattedCode)
      case Formatted.Failure(failure) => Left(failure.toString)
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
