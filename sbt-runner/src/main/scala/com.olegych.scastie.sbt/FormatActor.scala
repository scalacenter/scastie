package com.olegych.scastie
package sbt

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, SupervisorStrategy}
import com.olegych.scastie.api.{FormatRequest, FormatResponse, ScalaTarget}
import com.olegych.scastie.util.FormatReq
import org.scalafmt.config.{ScalafmtConfig, ScalafmtRunner}
import org.scalafmt.{Formatted, Scalafmt}
import org.slf4j.LoggerFactory

object FormatActor {
  private val log = LoggerFactory.getLogger(getClass)

  private[sbt] def format(code: String, isWorksheetMode: Boolean, scalaTarget: ScalaTarget): Either[String, String] = {
    log.info(s"format (isWorksheetMode: $isWorksheetMode)")
    log.info(code)

    val config: ScalafmtConfig = {
      val dialect =
        if (scalaTarget.scalaVersion.startsWith("2.12")) ScalafmtRunner.Dialect.scala212
        else if (scalaTarget.scalaVersion.startsWith("2.13")) ScalafmtRunner.Dialect.scala213
        else if (scalaTarget.scalaVersion.startsWith("3")) scala.meta.dialects.Scala3
        else ScalafmtRunner.Dialect.scala213

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
      case Formatted.Failure(failure)       => Left(failure.toString)
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
