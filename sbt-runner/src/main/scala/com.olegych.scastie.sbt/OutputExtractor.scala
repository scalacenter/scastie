package com.olegych.scastie.sbt

import java.time.Instant

import scastie.api._
import scastie.runtime.api._
import com.olegych.scastie.instrumentation.Instrument
import com.olegych.scastie.sbt.SbtProcess._
import org.slf4j.LoggerFactory

import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.parser._

import RuntimeCodecs._

import scala.util.control.NonFatal

class OutputExtractor(getScalaJsContent: () => Option[String],
                      getScalaJsSourceMapContent: () => Option[String],
                      isProduction: Boolean,
                      promptUniqueId: String) {
  def extractProgress(output: ProcessOutput, sbtRun: SbtRun, isReloading: Boolean): SnippetProgress = {
    import sbtRun._

    val problems = extractProblems(output.line, Instrument.getMessageLineOffset(inputs), inputs.isWorksheetMode)
    val instrumentations = extract[List[Instrumentation]](output.line)
    val runtimeError = extractRuntimeError(output.line, Instrument.getExceptionLineOffset(inputs))
    val consoleOutput = extract[ConsoleOutput](output.line)
    println(consoleOutput)
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

    val userOutput =
      if (problems.toList.flatten.isEmpty
          && instrumentations.toList.flatten.isEmpty
          && runtimeError.isEmpty
          && !isDone
          && !isHiddenSbtMessage
          && !isReloading
          && consoleOutput.isEmpty)
        Some(output)
      else None

    val (scalaJsContent, scalaJsSourceMapContent) =
      if (isDone && isScalaJs && problems.isEmpty) {
        (getScalaJsContent(), getScalaJsSourceMapContent())
      } else {
        (None, None)
      }

    val isSbtError = output.line.startsWith("[error]") && isReloading

    val isReallyDone = (isDone && !isReloading) || isSbtError

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
      sbtOutput = sbtProcessOutput,
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

  private def extractProblems(
      line: String,
      lineOffset: Int,
      isWorksheetMode: Boolean
  ): Option[List[Problem]] = {
    val problems = extract[List[Problem]](line)

    val problemsWithOffset = problems.map {
      _.map(problem => problem.copy(line = problem.line.map(lineNumber => (lineNumber + lineOffset) max 1)))
    }

    def annoying(in: Problem): Boolean = {
      in.severity == Warning &&
      in.message == "a pure expression does nothing in statement position; you may be omitting necessary parentheses"
    }

    if (isWorksheetMode) problemsWithOffset.map(_.filterNot(annoying))
    else problemsWithOffset
  }

  private def extractRuntimeError(line: String, lineOffset: Int): Option[RuntimeError] = {
    extract[RuntimeErrorWrap](line).flatMap {
      _.error.map { error =>
        val noStackTraceError = if (error.message.contains("No main class detected.")) error.copy(fullStack = "") else error
        val errorWithOffset = noStackTraceError.copy(
          line = noStackTraceError.line.map(lineNumber => (lineNumber + lineOffset) max 1)
        )
        errorWithOffset
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
