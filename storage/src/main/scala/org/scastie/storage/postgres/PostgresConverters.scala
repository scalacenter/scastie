package org.scastie.storage.postgres

import org.scastie.api
import org.scastie.api.BaseInputs
import org.scastie.api.Problem
import org.scastie.api.ProcessOutput
import org.scastie.api.SbtInputs
import org.scastie.api.ScalaCliInputs
import org.scastie.api.ScalaDependency
import org.scastie.api.SnippetId
import org.scastie.api.SnippetProgress
import org.scastie.api.RuntimeCodecs._
import org.scastie.runtime.api._
import org.scastie.runtime.api.Instrumentation

import io.circe.parser._
import io.circe.syntax._
import io.scalaland.chimney.dsl._
import scalasql.Sc

trait PostgresConverters {

  protected def fromPostgresSnippet(
    pgSnippet: PostgresSnippets[Sc],
    inputs: BaseInputs,
    progresses: List[SnippetProgress]
  ): Snippet = {
    val snippetId = SnippetId.fromString(pgSnippet.snippetId)
    Snippet(
      simpleSnippetId = pgSnippet.simpleSnippetId,
      username = pgSnippet.username,
      snippetId = snippetId,
      inputs = inputs,
      progresses = progresses,
      scalaJsContent = pgSnippet.scalaJsContent,
      scalaJsSourceMapContent = pgSnippet.scalaJsSourceMapContent,
      time = pgSnippet.time
    )
  }

  protected def toPostgresSnippet(
    snippetId: SnippetId,
    inputsHash: String,
    isShowingInUserProfile: Boolean,
    forked: Option[SnippetId] = None
  ): PostgresSnippets[Sc] = {
    val username: Option[String] = snippetId.user match {
      case Some(u) => Some(u.login)
      case None    => None
    }
    PostgresSnippets[Sc](
      simpleSnippetId = snippetId.base64UUID,
      username = username,
      snippetId = snippetId.url,
      inputsHash = inputsHash,
      isShowingInUserProfile = isShowingInUserProfile,
      forkedSnippetId = forked.map(_.url),
      scalaJsContent = "",
      scalaJsSourceMapContent = "",
      time = System.currentTimeMillis()
    )
  }

  def toPostgresProgress(snippetId: String, progress: SnippetProgress): PostgresProgresses[Sc] = progress
    .into[PostgresProgresses[Sc]]
    .withFieldConst(_.snippetId, snippetId)
    .withFieldConst(_.id, 0L)
    .withFieldComputed(_.runtimeError, _.runtimeError.map(_.asJson.noSpaces))
    .transform

  def fromPostgresProgress(
    pg: PostgresProgresses[Sc],
    userOutput: Option[ProcessOutput] = None,
    buildOutput: Option[ProcessOutput] = None,
    compilationInfos: List[Problem] = Nil,
    instrumentations: List[Instrumentation] = Nil
  ): SnippetProgress = {
    SnippetProgress(
      ts = pg.ts,
      id = None,
      snippetId = Some(SnippetId.fromString(pg.snippetId)),
      userOutput = userOutput,
      buildOutput = buildOutput,
      compilationInfos = compilationInfos,
      instrumentations = instrumentations,
      runtimeError = pg.runtimeError.flatMap(decode[RuntimeError](_).toOption),
      scalaJsContent = pg.scalaJsContent,
      scalaJsSourceMapContent = pg.scalaJsSourceMapContent,
      isDone = pg.isDone,
      isTimeout = pg.isTimeout,
      isSbtError = pg.isSbtError,
      isForcedProgramMode = pg.isForcedProgramMode
    )
  }

  private def hashInputs(inputs: BaseInputs): String = {
    import java.security.MessageDigest

    val cleanedInputs = inputs.copyBaseInput(
      forked = None
    )

    val md    = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(cleanedInputs.asJson.noSpaces.getBytes("UTF-8"))
    bytes.map("%02x".format(_)).mkString
  }

  def toPostgresInputs(inputs: BaseInputs): PostgresInputs[Sc] = {
    inputs match {
      case ScalaCliInputs(isWorksheetMode, code, target, isShowingInUserProfile, forked, libraries) =>
        PostgresInputs[Sc](
          hash = hashInputs(inputs),
          inputType = "ScalaCli",
          code = code,
          target = target.asJson.noSpaces,
          libraries = libraries.map(_.asJson.noSpaces).mkString(","),
          isWorksheet = isWorksheetMode,
          sbtConfigExtra = None,
          sbtConfigSaved = None,
          sbtPluginsConfigExtra = None,
          sbtPluginsConfigSaved = None,
          librariesFromList = None
        )
      case sbtInputs: SbtInputs => PostgresInputs[Sc](
          hash = hashInputs(inputs),
          inputType = "Sbt",
          code = sbtInputs.code,
          target = sbtInputs.target.asJson.noSpaces,
          libraries = sbtInputs.libraries.map(_.asJson.noSpaces).mkString(","),
          isWorksheet = sbtInputs.isWorksheetMode,
          sbtConfigExtra = Some(sbtInputs.sbtConfigExtra),
          sbtConfigSaved = sbtInputs.sbtConfigSaved,
          sbtPluginsConfigExtra = Some(sbtInputs.sbtPluginsConfigExtra),
          sbtPluginsConfigSaved = sbtInputs.sbtPluginsConfigSaved,
          librariesFromList = Some(sbtInputs.librariesFromList.mkString(","))
        )
    }
  }

  def fromPostgresInputs(
    pg: PostgresInputs[Sc],
    isShowingInUserProfile: Boolean,
    forked: Option[SnippetId]
  ): Option[BaseInputs] = {
    pg.inputType match {
      case "ScalaCli" => Some(
          ScalaCliInputs(
            isWorksheetMode = pg.isWorksheet,
            code = pg.code,
            target = decode[api.ScalaCli](pg.target).getOrElse(api.ScalaCli.default),
            isShowingInUserProfile = isShowingInUserProfile,
            forked = forked,
            libraries =
              if (pg.libraries.isEmpty) Set.empty
              else pg.libraries
                .split(",")
                .toSet
                .flatMap((libStr: String) => decode[ScalaDependency](libStr).toOption)
          )
        )
      case "Sbt" => Some(
          SbtInputs(
            isWorksheetMode = pg.isWorksheet,
            code = pg.code,
            target = decode[api.SbtScalaTarget](pg.target).getOrElse(api.Scala3.default),
            libraries =
              if (pg.libraries.isEmpty) Set.empty
              else pg.libraries
                .split(",")
                .toSet
                .flatMap((libStr: String) => decode[ScalaDependency](libStr).toOption),
            librariesFromList = pg.librariesFromList
              .map(
                _.split(",").toList
                  .filter(_.nonEmpty)
                  .flatMap { libStr =>
                    import io.circe.parser._
                    decode[(ScalaDependency, api.Project)](libStr).toOption
                  }
              )
              .getOrElse(List.empty),
            sbtConfigExtra = pg.sbtConfigExtra.getOrElse(""),
            sbtConfigSaved = pg.sbtConfigSaved,
            sbtPluginsConfigExtra = pg.sbtPluginsConfigExtra.getOrElse(""),
            sbtPluginsConfigSaved = pg.sbtPluginsConfigSaved,
            isShowingInUserProfile = isShowingInUserProfile,
            forked = forked
          )
        )
      case _ => None
    }
  }

  def toPostgresInstrumentation(progressId: Long, instr: Instrumentation): PostgresInstrumentations[Sc] = instr
    .into[PostgresInstrumentations[Sc]]
    .withFieldConst(_.progressId, progressId)
    .withFieldComputed(_.position, _.position.asJson.noSpaces)
    .withFieldComputed(_.render, _.render.asJson.noSpaces)
    .transform

  def fromPostgresInstrumentation(pg: PostgresInstrumentations[Sc]): Option[Instrumentation] = for {
    pos    <- decode[Position](pg.position).toOption
    render <- decode[Render](pg.render).toOption
  } yield Instrumentation(pos, render)

  def toPostgresCompilationInfo(progressId: Long, problem: Problem): PostgresCompilationInfos[Sc] = problem
    .into[PostgresCompilationInfos[Sc]]
    .withFieldConst(_.progressId, progressId)
    .withFieldComputed(_.severity, _.severity.asJson.noSpaces)
    .transform

  def fromPostgresCompilationInfo(pg: PostgresCompilationInfos[Sc]): Option[Problem] = {
    for {
      severity <- decode[org.scastie.api.Severity](pg.severity).toOption
    } yield Problem(
      severity = severity,
      startLine = pg.startLine,
      endLine = pg.endLine,
      startColumn = pg.startColumn,
      endColumn = pg.endColumn,
      message = pg.message
    )
  }

  def toPostgresUserOutput(progressId: Long, output: ProcessOutput): PostgresUserOutputs[Sc] = output
    .into[PostgresUserOutputs[Sc]]
    .withFieldConst(_.progressId, progressId)
    .withFieldComputed(_.processOutput, _.asJson.noSpaces)
    .transform

  def fromPostgresUserOutput(pg: PostgresUserOutputs[Sc]): Option[ProcessOutput] =
    decode[ProcessOutput](pg.processOutput).toOption

  def toPostgresBuildOutput(progressId: Long, output: ProcessOutput): PostgresBuildOutputs[Sc] = output
    .into[PostgresBuildOutputs[Sc]]
    .withFieldConst(_.progressId, progressId)
    .withFieldComputed(_.processOutput, _.asJson.noSpaces)
    .transform

  def fromPostgresBuildOutput(pg: PostgresBuildOutputs[Sc]): Option[ProcessOutput] =
    decode[ProcessOutput](pg.processOutput).toOption
}
