package com.olegych.scastie.sbt

import com.olegych.scastie.proto._
import com.olegych.scastie.api.FormatResponseHelper

import org.scalafmt.{Scalafmt, Formatted}
import org.scalafmt.config.{ScalafmtConfig, ScalafmtRunner}

import akka.actor.Actor

import org.slf4j.LoggerFactory

import java.io.{PrintWriter, StringWriter}

class FormatActor() extends Actor {
  private val log = LoggerFactory.getLogger(getClass)

  private def format(code: String,
                     worksheetMode: Boolean,
                     targetType: ScalaTargetType): FormatResponse.Value = {
    log.info(s"format (worksheetMode: $worksheetMode)")
    log.info(code)

    val config =
      if (worksheetMode && targetType != ScalaTargetType.Dotty)
        ScalafmtConfig.default.copy(runner = ScalafmtRunner.sbt)
      else
        ScalafmtConfig.default

    Scalafmt.format(code, style = config) match {
      case Formatted.Success(formattedCode) =>
        FormatResponseHelper.success(formattedCode)

      case Formatted.Failure(failure) =>
        val errors = new StringWriter()
        failure.printStackTrace(new PrintWriter(errors))
        val fullStack = errors.toString
        FormatResponseHelper.failure(fullStack)
    }
  }

  override def receive: Receive = {
    case FormatRequest(code, worksheetMode, targetType) =>
      sender ! FormatResponse(format(code, worksheetMode, targetType))
  }
}
