package org.scastie.instrumentation

import java.io.{PrintWriter, StringWriter}
import java.time.Instant

import org.scastie.api._

import scala.meta.inputs.Input
import scala.meta.parsers.Parsed

case class InstrumentationFailureReport(message: String, line: Option[Int], startColumn: Option[Int] = None, endColumn: Option[Int] = None) {
  def toProgress(snippetId: SnippetId): SnippetProgress = {
    SnippetProgress.default.copy(
      ts = Some(Instant.now.toEpochMilli),
      snippetId = Some(snippetId),
      compilationInfos = List(Problem(Error, line, startColumn, endColumn, message))
    )
  }
}

object InstrumentedInputs {
  def apply(inputs0: BaseInputs): Either[InstrumentationFailureReport, InstrumentedInputs] = {
    if (inputs0.isWorksheetMode) {
      val instrumented = Instrument(inputs0.code, inputs0.target).map {
        case InstrumentationSuccess(instrumentedCode, positionMapper) =>
          (inputs0.copyBaseInput(code = instrumentedCode), positionMapper)
      }

      instrumented match {
        case Right((inputs, positionMapper)) => Right(
            InstrumentedInputs(
              inputs = inputs,
              isForcedProgramMode = false,
              positionMapper = positionMapper
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
              val isScalaCli = inputs0.target match {
                case _: ScalaCli => true
                case _ => false
              }
              val positionMapper = PositionMapper(error.pos.input.text, isScalaCli)
              val errorLine = positionMapper.mapLine(error.pos.startLine + 1) max 1
              val errorStartCol = error.pos.startColumn + 1
              val errorEndCol = error.pos.endColumn + 1

              Right(InstrumentedInputs(
                inputs = inputs0.copyBaseInput(code = error.pos.input.text),
                isForcedProgramMode = false,
                optionalParsingError = Some(InstrumentationFailureReport(error.message, Some(errorLine), Some(errorStartCol), Some(errorEndCol))),
                positionMapper = Some(positionMapper)
              ))

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
    positionMapper: Option[PositionMapper] = None
)
