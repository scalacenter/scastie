package com.olegych.scastie
package sbt

import api.{FormatRequest, FormatResponse}

import akka.actor.Actor

import org.scalafmt.{Scalafmt, Formatted}
import org.scalafmt.config.{ScalafmtConfig, ScalafmtRunner}

import org.slf4j.LoggerFactory

import java.io.{PrintWriter, StringWriter}

class FormatActor() extends Actor {
  private val log = LoggerFactory.getLogger(getClass)

  private def format(code: String,
                     worksheetMode: Boolean): Either[String, String] = {
    log.info(s"format (worksheetMode: $worksheetMode)")
    log.info(code)

    val config =
      if (worksheetMode)
        ScalafmtConfig.default.copy(runner = ScalafmtRunner.sbt)
      else
        ScalafmtConfig.default

    Scalafmt.format(code, style = config) match {
      case Formatted.Success(formattedCode) => Right(formattedCode)
      case Formatted.Failure(failure) => {
        val errors = new StringWriter()
        failure.printStackTrace(new PrintWriter(errors))
        val fullStack = errors.toString()
        Left(fullStack)
      }
    }
  }

  def receive = {
    case FormatRequest(code, worksheetMode) => {
      sender ! FormatResponse(format(code, worksheetMode))
    }
  }
}
