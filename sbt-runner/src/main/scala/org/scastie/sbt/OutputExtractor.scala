package org.scastie.sbt

import java.time.Instant

import org.scastie.api._
import org.scastie.runtime.api._
import org.scastie.instrumentation.Instrument
import org.scastie.sbt.SbtProcess._
import org.slf4j.LoggerFactory

import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.parser._

import RuntimeCodecs._

import scala.meta.inputs.Input
import scala.util.control.NonFatal
import org.scastie.sbt
import org.scastie.instrumentation.PositionMapper

class OutputExtractor(getScalaJsContent: () => Option[String],
                      getScalaJsSourceMapContent: () => Option[String],
                      isProduction: Boolean,
                      promptUniqueId: String) {
  def extractProgress(output: ProcessOutput, sbtRun: SbtRun, isReloading: Boolean): SnippetProgress = {
    import sbtRun._

    val problems = extractProblems(output.line, sbtRun, Instrument.getMessageLineOffset(inputs.isWorksheetMode))
    val instrumentations = extract[List[Instrumentation]](output.line)
    val runtimeError = extractRuntimeError(output.line, sbtRun, Instrument.getExceptionLineOffset(inputs.isWorksheetMode))
    val consoleOutput = extract[ConsoleOutput](output.line)
    // sbt plugin is not loaded at this stage. we need to drop those messages
    val hiddenInitializationMessages = List(
      "WARNING: A terminally deprecated method in java.lang.System has been called",
      "WARNING: System::setSecurityManager has been called",
      "WARNING: Please consider reporting this to the maintainers",
      "WARNING: System::setSecurityManager will be removed in a future release",
    )

    val isHiddenSbtMessage =
      hiddenInitializationMessages.exists(message => output.line.toLowerCase().startsWith(message.toLowerCase()))

    val isDone = output.line == promptUniqueId

    val isScalaJs = inputs.target.targetType == ScalaTargetType.JS

    val isSbtError = output.line.startsWith("[error]") && isReloading

    val userOutput =
      if (problems.toList.flatten.isEmpty
          && instrumentations.toList.flatten.isEmpty
          && runtimeError.isEmpty
          && !isDone
          && !isHiddenSbtMessage
          && !isReloading
          && consoleOutput.isEmpty)
        Some(output)
      else if (isSbtError)
        Some(output)
      else None

    val (scalaJsContent, scalaJsSourceMapContent) =
      if (isDone && isScalaJs && problems.isEmpty) {
        (getScalaJsContent(), getScalaJsSourceMapContent())
      } else {
        (None, None)
      }

    val isReallyDone = isDone && !isReloading

    val sbtProcessOutput =
      consoleOutput match {
        case Some(SbtOutput(output)) if !isHiddenSbtMessage => Some(output)
        case _ => None
      }

    SnippetProgress(
      ts = Some(Instant.now.toEpochMilli),
      id = None,
      snippetId = Some(snippetId),
      userOutput = userOutput,
      buildOutput = sbtProcessOutput,
      compilationInfos = problems.getOrElse(Nil),
      instrumentations = instrumentations.getOrElse(Nil),
      runtimeError = runtimeError,
      scalaJsContent = scalaJsContent,
      scalaJsSourceMapContent = scalaJsSourceMapContent.map(
        remapSourceMap(snippetId)
      ),
      isDone = isReallyDone,
      isTimeout = false,
      isSbtError = isSbtError,
      isForcedProgramMode = isForcedProgramMode
    )
  }

  private implicit val sourceMapEncoder: Encoder[SourceMap] = deriveEncoder[SourceMap]
  private implicit val sourceMapDecoder: Decoder[SourceMap] = deriveDecoder[SourceMap]

  private case class SourceMap(
      version: Int,
      file: String,
      mappings: String,
      sources: List[String],
      names: List[String],
      lineCount: Int
  )

  private def remapSourceMap(
      snippetId: SnippetId
  )(sourceMapRaw: String): String = {
    decode[SourceMap](sourceMapRaw).toOption
      .map { sourceMap =>
        val sourceMap0 =
          sourceMap.copy(
            sources = sourceMap.sources.map(
              source =>
                if (source.startsWith(Js.sourceUUID)) {
                  val host =
                    if (isProduction) "https://scastie.scala-lang.org"
                    else "http://localhost:9000"

                  host + snippetId.scalaJsUrl(Js.sourceFilename)
                } else source
            )
          )

        sourceMap0.asJson.noSpaces
      }
      .getOrElse(sourceMapRaw)
  }

  private def mapColumn(column: Option[Int], line: Option[Int], positionMapper: Option[PositionMapper] = None): Option[Int] = {
    positionMapper match {
      case Some(mapper) => 
        (column, line) match {
          case (Some(c), Some(l)) => Some(mapper.mapColumn(l, c))
          case _ => column
        }
      case None => column
    }
  }

  private def mapLine(line: Option[Int], positionMapper: Option[PositionMapper] = None, offset: Int = 0): Option[Int] = {
    positionMapper match {
      case Some(mapper) => line.map(mapper.mapLine)
      case None => line.map(_ + offset)
    }
  }

  private def extractProblems(
      line: String,
      sbtRun: SbtRun,
      lineOffset: Int
  ): Option[List[Problem]] = {
    val problems = extract[List[Problem]](line)

    val problemsWithMappedPositions = problems.map {
      _.map(problem => {
        val mappedLine = mapLine(problem.line, sbtRun.positionMapper, lineOffset)
        val mappedEndLine = mapLine(problem.endLine, sbtRun.positionMapper, lineOffset)
        val mappedStartColumn = mapColumn(problem.startColumn.map(_ + 1), problem.line, sbtRun.positionMapper)
        val mappedEndColumn = mapColumn(problem.endColumn.map(_ + 1), problem.endLine, sbtRun.positionMapper)

        problem.copy(
          line = mappedLine,
          endLine = mappedEndLine,
          startColumn = mappedStartColumn,
          endColumn = mappedEndColumn
        )
      })
    }

    def annoying(in: Problem): Boolean = {
      in.severity == Warning &&
      in.message == "a pure expression does nothing in statement position; you may be omitting necessary parentheses"
    }

    if (sbtRun.inputs.isWorksheetMode) problemsWithMappedPositions.map(_.filterNot(annoying))
    else problemsWithMappedPositions
  }

  private def extractRuntimeError(line: String, sbtRun: SbtRun, lineOffset: Int): Option[RuntimeError] = {
    extract[RuntimeErrorWrap](line).flatMap {
      _.error.map { error =>
        val noStackTraceError = if (error.message.contains("No main class detected.")) error.copy(fullStack = "") else error
        val errorWithMappedLine = noStackTraceError.copy(
          line = noStackTraceError.line.map(
            line => mapLine(Some(line), sbtRun.positionMapper, lineOffset).getOrElse(line)
          )
        )
        errorWithMappedLine
      }
    }
  }

  private def extract[T: Decoder](line: String): Option[T] = {
    try {
      decode[T](line).toOption
    } catch {
      case NonFatal(e) => None
    }
  }

  private implicit val sbtOutputDecoder: Decoder[SbtOutput] = deriveDecoder[SbtOutput]

}
