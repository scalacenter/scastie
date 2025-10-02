package org.scastie.instrumentation

import java.io.{PrintWriter, StringWriter}
import java.time.Instant
import scala.meta.inputs.Input
import scala.meta.parsers.Parsed

import org.scastie.api._

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

  def apply(inputs0: BaseInputs): Either[InstrumentationFailureReport, InstrumentedInputs] = {
    if (inputs0.isWorksheetMode) {
      val instrumented = Instrument(inputs0.code, inputs0.target).map {
        case InstrumentationSuccess(instrumentedCode, lineMapper) =>
          (inputs0.copyBaseInput(code = instrumentedCode), lineMapper)
      }

      instrumented match {
        case Right((inputs, lineMapping)) => Right(
            InstrumentedInputs(
              inputs = inputs,
              isForcedProgramMode = false,
              lineMapping = lineMapping
            )
          )
        case Left(error) =>
          import InstrumentationFailure._

          error match {
            case HasMainMethod =>
              Right(InstrumentedInputs(inputs0.copyBaseInput(isWorksheetMode = false), isForcedProgramMode = true))

            case UnsupportedDialect =>
              Left(InstrumentationFailureReport("This Scala target does not have a worksheet mode", None))

            case ParsingError(error) =>
              val lineOffset = Instrument.getParsingLineOffset(inputs0.isWorksheetMode)
              val errorLine = (error.pos.startLine + lineOffset) max 1
              Right(
                InstrumentedInputs(
                  inputs = inputs0.copyBaseInput(code = error.pos.input.text),
                  isForcedProgramMode = false,
                  optionalParsingError = Some(InstrumentationFailureReport(error.message, Some(errorLine)))
                )
              )

            case InternalError(exception) =>
              val errors = new StringWriter()
              exception.printStackTrace(new PrintWriter(errors))
              val fullStack = errors.toString

              Left(InstrumentationFailureReport(fullStack, None))
          }

      }
    } else {
      Right(InstrumentedInputs(inputs0, isForcedProgramMode = false))
    }
  }

}

case class InstrumentedInputs(
    inputs: BaseInputs,
    isForcedProgramMode: Boolean,
    optionalParsingError: Option[InstrumentationFailureReport] = None,
    lineMapping: Int => Int = identity
)
