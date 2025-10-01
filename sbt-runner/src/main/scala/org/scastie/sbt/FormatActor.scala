package org.scastie
package sbt

import scala.meta._

import akka.actor.Actor
import org.scalafmt.config.NamedDialect
import org.scalafmt.config.ScalafmtConfig
import org.scalafmt.Formatted
import org.scalafmt.Scalafmt
import org.scastie.api.FormatRequest
import org.scastie.api.FormatResponse
import org.scastie.api.ScalaTarget
import org.slf4j.LoggerFactory

object FormatActor {

  private def configFor(nd: NamedDialect): ScalafmtConfig = ScalafmtConfig.default.withDialect(nd)

  private[sbt] def format(code: String, scalaTarget: api.ScalaTarget): Either[String, String] = {
    val config: ScalafmtConfig =
      if (scalaTarget.scalaVersion.startsWith("2.12")) configFor(dialects.Scala212)
      else if (scalaTarget.scalaVersion.startsWith("3")) configFor(dialects.Scala3)
      else configFor(dialects.Scala213)

    Scalafmt.format(code, style = config) match {
      case Formatted.Success(formattedCode) => Right(formattedCode)
      case Formatted.Failure(failure)       => Left(failure.toString)
    }
  }

}

class FormatActor() extends Actor {
  import FormatActor._
  private val log = LoggerFactory.getLogger(getClass)

  override def receive: Receive = { case api.FormatRequest(code, isWorksheetMode, scalaTarget) =>
    log.info(s"format (isWorksheetMode: $isWorksheetMode)")
    log.info(code)

    format(code, scalaTarget) match {
      case Left(value)  => sender() ! FormatResponse(code)
      case Right(value) => sender() ! FormatResponse(value)
    }
  }

}
