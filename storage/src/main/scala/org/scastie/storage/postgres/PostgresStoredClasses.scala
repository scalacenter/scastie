package org.scastie.storage.postgres

import org.scastie.api._
import scalasql.Table

case class Snippet(
  simpleSnippetId: String,
  username: Option[String],
  snippetId: SnippetId,
  inputs: BaseInputs,
  progresses: List[SnippetProgress],
  scalaJsContent: String,
  scalaJsSourceMapContent: String,
  time: Long
) {
  def toFetchResult: FetchResult = FetchResult.create(inputs, progresses)
  def some = Some(this)
}

/* Table: Snippets */
case class PostgresSnippets[T[_]](
  snippetId: T[String],
  simpleSnippetId: T[String],
  forkedSnippetId: T[Option[String]],
  username: T[Option[String]],
  inputsHash: T[String],
  isShowingInUserProfile: T[Boolean],
  scalaJsContent: T[String],
  scalaJsSourceMapContent: T[String],
  time: T[Long]
)
object PostgresSnippets extends Table[PostgresSnippets]

/* Table: Inputs */
case class PostgresInputs[T[_]](
  hash: T[String],
  inputType: T[String],
  code: T[String],
  target: T[String],
  libraries: T[String],
  isWorksheet: T[Boolean],
  sbtConfigExtra: T[Option[String]],
  sbtConfigSaved: T[Option[String]],
  sbtPluginsConfigExtra: T[Option[String]],
  sbtPluginsConfigSaved: T[Option[String]],
  librariesFromList: T[Option[String]]
)
object PostgresInputs extends Table[PostgresInputs]

/* Table: Progresses */
case class PostgresProgresses[T[_]](
  id: T[Long],
  snippetId: T[String],
  runtimeError: T[Option[String]],
  scalaJsContent: T[Option[String]],
  scalaJsSourceMapContent: T[Option[String]],
  isDone: T[Boolean],
  isTimeout: T[Boolean],
  isSbtError: T[Boolean],
  isForcedProgramMode: T[Boolean],
  ts: T[Option[Long]]
)
object PostgresProgresses extends Table[PostgresProgresses]

/* Table: CompilationInfos */
case class PostgresCompilationInfos[T[_]](
  // id: T[Long],
  progressId: T[Long],
  severity: T[String],
  line: T[Option[Int]],
  endLine: T[Option[Int]],
  startColumn: T[Option[Int]],
  endColumn: T[Option[Int]],
  message: T[String]
)
object PostgresCompilationInfos extends Table[PostgresCompilationInfos]

/* Table: Instrumentations */
case class PostgresInstrumentations[T[_]](
  // id: T[Long],
  progressId: T[Long],
  position: T[String],
  render: T[String]
)
object PostgresInstrumentations extends Table[PostgresInstrumentations]

/* Table: UserOutputs */
case class PostgresUserOutputs[T[_]](
  // id: T[Long],
  progressId: T[Long],
  processOutput: T[String]
)
object PostgresUserOutputs extends Table[PostgresUserOutputs]

/* Table: BuildOutputs */
case class PostgresBuildOutputs[T[_]](
  // id: T[Long],
  progressId: T[Long],
  processOutput: T[String]
)
object PostgresBuildOutputs extends Table[PostgresBuildOutputs]
