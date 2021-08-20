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
import org.scalafmt.config.ScalafmtRunner.Dialect
import org.slf4j.LoggerFactory

object FormatActor {
  private[sbt] def format(code: String, isWorksheetMode: Boolean, scalaTarget: ScalaTarget): Either[String, String] = {
    val config: ScalafmtConfig = {
      val dialect = scalaTarget match {
        // TODO: Support 2.10, 2.11, 2.12? Maybe wait for abstract ScalaTarget.scalaVersion
        case ScalaTarget.Jvm(_) =>
          ScalafmtRunner.Dialect.scala213
        case ScalaTarget.Js(scalaVersion, _) =>
          if (scalaVersion.startsWith("2")) ScalafmtRunner.Dialect.scala213
          else if (scalaVersion.startsWith("3")) scala.meta.dialects.Scala3
          else ScalafmtRunner.Dialect.scala213
        case ScalaTarget.Scala3(_) => 
          scala.meta.dialects.Scala3
        case _ => ScalafmtRunner.Dialect.scala213
      }

      val runner = {
        val tmp = ScalafmtRunner(dialect = dialect)
        if (isWorksheetMode && scalaTarget.hasWorksheetMode)
          tmp.forSbt
        else tmp
      }
      ScalafmtConfig.default.copy(runner = runner)
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
