package com.olegych.scastie
package sbt

import api.{FormatRequest, FormatResponse, ScalaTarget}

import akka.actor.Actor

import org.scalafmt.{Scalafmt, Formatted}
import org.scalafmt.config.{ScalafmtConfig, ScalafmtRunner}

import org.slf4j.LoggerFactory

import java.io.{PrintWriter, StringWriter}

class FormatActor() extends Actor {
  private val log = LoggerFactory.getLogger(getClass)

  private def format(code: String, isWorksheetMode: Boolean, scalaTarget: ScalaTarget): Either[String, String] = {
    log.info(s"format (isWorksheetMode: $isWorksheetMode)")
    log.info(code)

    val config =
      if (isWorksheetMode && scalaTarget.hasWorksheetMode)
        ScalafmtConfig.default.copy(runner = ScalafmtRunner.sbt)
      else
        ScalafmtConfig.default

    Scalafmt.format(code, style = config) match {
      case Formatted.Success(formattedCode) => Right(formattedCode)
      case Formatted.Failure(failure) =>
        Left(failure.toString)
    }
  }

  override def receive: Receive = {
    case FormatRequest(code, isWorksheetMode, scalaTarget) =>
      sender ! FormatResponse(format(code, isWorksheetMode, scalaTarget))
  }
}
