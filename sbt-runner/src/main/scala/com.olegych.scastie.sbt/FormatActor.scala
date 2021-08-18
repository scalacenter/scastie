package com.olegych.scastie
package sbt

import akka.actor.typed.{Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import com.olegych.scastie.api.{FormatRequest, FormatResponse, ScalaTarget}
import com.olegych.scastie.util.FormatReq
import org.scalafmt.config.{ScalafmtConfig, ScalafmtRunner}
import org.scalafmt.{Formatted, Scalafmt}
import org.slf4j.LoggerFactory

object FormatActor {
  private val log = LoggerFactory.getLogger(getClass)

  private def format(code: String, isWorksheetMode: Boolean, scalaTarget: ScalaTarget): Either[String, String] = {
    log.info(s"format (isWorksheetMode: $isWorksheetMode)")
    log.info(code)

    val config: ScalafmtConfig = {
      val withDialect =  scalaTarget match {
        case dotty: ScalaTarget.Scala3 => ScalafmtConfig.default.withDialect(scala.meta.dialects.Scala3)
        case _ =>  ScalafmtConfig.default
      }

      if (isWorksheetMode && scalaTarget.hasWorksheetMode)
        withDialect.copy(runner = ScalafmtRunner.sbt.copy(dialect=withDialect.runner.dialect))
      else withDialect
    }

    Scalafmt.format(code, style = config) match {
      case Formatted.Success(formattedCode) => Right(formattedCode)
      case Formatted.Failure(failure) => Left(failure.toString)
    }
  }

  def apply(): Behavior[FormatReq] =
    Behaviors.supervise {
      Behaviors.receiveMessage[FormatReq] {
        case FormatReq(sender, FormatRequest(code, isWorksheetMode, scalaTarget)) =>
          sender ! FormatResponse(format(code, isWorksheetMode, scalaTarget))
          Behaviors.same
      }
    }.onFailure(SupervisorStrategy.resume)
}
