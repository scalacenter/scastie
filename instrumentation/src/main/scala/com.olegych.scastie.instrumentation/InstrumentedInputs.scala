package com.olegych.scastie.instrumentation

import java.io.{PrintWriter, StringWriter}
import java.time.Instant

import com.olegych.scastie.api._

import scala.meta.parsers.Parsed

case class InstrumentationFailureReport(message: String, line: Option[Int]) {
  def toProgress(snippetId: SnippetId): SnippetProgress = {
    SnippetProgress.default.copy(
      ts = Some(Instant.now.toEpochMilli),
      snippetId = Some(snippetId),
      compilationInfos = List(Problem(Error, line, message))
    )
  }
}

object InstrumentedInputs {
  def apply(inputs0: Inputs): Either[InstrumentationFailureReport, InstrumentedInputs] = {
    if (inputs0.isWorksheetMode) {
      val instrumented = Instrument(inputs0.code, inputs0.target).map { instrumentedCode =>
        inputs0.copy(code = instrumentedCode)
      }

      instrumented match {
        case Right(inputs) =>
          success(inputs)

        case Left(error) =>
          import InstrumentationFailure._

          error match {
            case HasMainMethod =>
              Right(InstrumentedInputs(inputs0.copy(_isWorksheetMode = false), isForcedProgramMode = true))

            case UnsupportedDialect =>
              Left(InstrumentationFailureReport("This Scala target does not have a worksheet mode", None))

            case ParsingError(error) =>
              val lineOffset = Instrument.getParsingLineOffset(inputs0)
              Left(InstrumentationFailureReport(error.message, Some(error.pos.startLine + lineOffset)))

            case InternalError(exception) =>
              val errors = new StringWriter()
              exception.printStackTrace(new PrintWriter(errors))
              val fullStack = errors.toString

              Left(InstrumentationFailureReport(fullStack, None))
          }

      }
    } else {
      success(inputs0)
    }
  }

  private def success(inputs: Inputs): Either[InstrumentationFailureReport, InstrumentedInputs] = {
    Right(InstrumentedInputs(inputs, isForcedProgramMode = false))
  }
}

case class InstrumentedInputs(
    inputs: Inputs,
    isForcedProgramMode: Boolean
)
