package com.olegych.scastie.instrumentation

import com.olegych.scastie.api._
import scala.meta.parsers.Parsed
import java.io.{PrintWriter, StringWriter}

case class InstrumentationFailureReport(message: String, line: Option[Int]) {
  def toProgress(snippetId: SnippetId): SnippetProgress = {
    SnippetProgress.default
      .copy(
        snippetId = Some(snippetId),
        compilationInfos = List(Problem(Error, line, message))
      )
  }
}

object InstrumentedInputs {
  def apply(
      inputs0: Inputs
  ): Either[InstrumentationFailureReport, InstrumentedInputs] = {
    if (inputs0.isWorksheetMode && inputs0.target.hasWorksheetMode) {
      val instrumented =
        Instrument(inputs0.code, inputs0.target).map(
          instrumentedCode => inputs0.copy(code = instrumentedCode)
        )

      instrumented match {
        case Right(inputs) => {
          success(inputs)
        }

        case Left(error) => {
          import InstrumentationFailure._

          error match {
            case HasMainMethod => {
              Right(
                InstrumentedInputs(
                  inputs0.copy(_isWorksheetMode = false),
                  isForcedProgramMode = true
                )
              )
            }

            case UnsupportedDialect => {
              Left(
                InstrumentationFailureReport(
                  "This Scala target does not have a worksheet mode",
                  None
                )
              )
            }

            case ParsingError(Parsed.Error(pos, message, _)) => {
              val lineOffset = Instrument.getLineOffset(isWorksheetMode = true)
              Left(
                InstrumentationFailureReport(
                  message,
                  Some(pos.start.line + lineOffset)
                )
              )
            }

            case InternalError(exception) => {
              val errors = new StringWriter()
              exception.printStackTrace(new PrintWriter(errors))
              val fullStack = errors.toString

              Left(
                InstrumentationFailureReport(
                  fullStack,
                  None
                )
              )
            }
          }
        }

      }
    } else {
      success(inputs0)
    }
  }

  private def success(
      inputs: Inputs
  ): Either[InstrumentationFailureReport, InstrumentedInputs] = {
    Right(InstrumentedInputs(inputs, isForcedProgramMode = false))
  }
}

case class InstrumentedInputs(
    inputs: Inputs,
    isForcedProgramMode: Boolean
)
