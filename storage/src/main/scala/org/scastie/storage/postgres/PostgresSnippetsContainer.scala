package org.scastie.storage.postgres

import scala.concurrent.Future

import org.scastie.api._
import org.scastie.storage.SnippetsContainer
import org.scastie.storage.UserLogin

import scalasql.core.DbClient
import scalasql.PostgresDialect._
import scalasql.Sc

trait PostgresSnippetsContainer extends SnippetsContainer with PostgresConverters {
  val db: DbClient.DataSource

  /** Insert a new snippet into the database */
  protected def insert(snippetId: SnippetId, inputs: BaseInputs): Future[Unit] = {
    val adjustedInputs = inputs match {
      case sbtInputs: SbtInputs => sbtInputs.withSavedConfig
      case _                    => inputs
    }
    val convertedInputs = toPostgresInputs(adjustedInputs)
    val inputsHash      = convertedInputs.hash
    val forkedSnippetId = inputs.forked
    val snippet         = toPostgresSnippet(snippetId, inputsHash, inputs.isShowingInUserProfile, forkedSnippetId)

    db.transaction { db =>
      val existingInput = db.run(
        PostgresInputs.select
          .filter(_.hash === convertedInputs.hash)
      )
      /* Insert the new input if it doesn't exist */
      if (existingInput.isEmpty) {
        db.run(PostgresInputs.insert.values(convertedInputs))
      }
      db.run(PostgresSnippets.insert.values(snippet))
    }
    Future.unit
  }

  def insertWithExistingId(snippetId: SnippetId, inputs: BaseInputs): Future[Unit] = {
    insert(snippetId, inputs)
  }

  private def readProgresses(snippetId: SnippetId): List[SnippetProgress] = {
    val query = PostgresProgresses.select
      .filter(_.snippetId === snippetId.url)
      .leftJoin(PostgresUserOutputs)(_.id === _.progressId)
      .leftJoin(PostgresBuildOutputs)(_._1.id === _.progressId)
      .leftJoin(PostgresCompilationInfos)(_._1._1.id === _.progressId)
      .leftJoin(PostgresInstrumentations)(_._1._1._1.id === _.progressId)
      .map { case ((((progress, userOutput), buildOutput), compilationInfo), instrumentation) =>
        (
          progress,
          userOutput,
          buildOutput,
          compilationInfo,
          instrumentation
        )
      }

    db.transaction { db =>
      val results = db.run(query).toList

      results
        .groupBy(_._1.id)
        .map { case (_, rows) =>
          val progress = rows.head._1

          val userOutputs = rows
            .flatMap(_._2)
            .map(fromPostgresUserOutput)

          val buildOutputs = rows
            .flatMap(_._3)
            .map(fromPostgresBuildOutput)

          val compilationInfos = rows
            .flatMap(_._4)
            .map(fromPostgresCompilationInfo)

          val instrumentations = rows
            .flatMap(_._5)
            .map(fromPostgresInstrumentation)

          fromPostgresProgress(
            progress,
            userOutputs.headOption.flatten,
            buildOutputs.headOption.flatten,
            compilationInfos.flatten,
            instrumentations.flatten
          )
        }
        .toList
    }
  }

  /* Inserts progress omitting the ID (for auto-increment) */
  private def insertProgress(convertedProgress: PostgresProgresses[Sc]): Long = {
    db.transaction { db =>
      db.run(
        PostgresProgresses.insert
          .columns(
            _.snippetId               := convertedProgress.snippetId,
            _.runtimeError            := convertedProgress.runtimeError,
            _.scalaJsContent          := convertedProgress.scalaJsContent,
            _.scalaJsSourceMapContent := convertedProgress.scalaJsSourceMapContent,
            _.isDone                  := convertedProgress.isDone,
            _.isTimeout               := convertedProgress.isTimeout,
            _.isSbtError              := convertedProgress.isSbtError,
            _.isForcedProgramMode     := convertedProgress.isForcedProgramMode,
            _.ts                      := convertedProgress.ts
          )
          .returning(_.id)
      ).head
    }
  }

  /** Append a new progress to an existing snippet */
  def appendOutput(progress: SnippetProgress): Future[Unit] = {
    progress.snippetId match {
      case Some(snippetId) =>
        val convertedProgress = toPostgresProgress(snippetId.url, progress)
        db.transaction { db =>
          val jsContent   = convertedProgress.scalaJsContent.getOrElse("")
          val jsSourceMap = convertedProgress.scalaJsSourceMapContent.getOrElse("")
          db.run(
            PostgresSnippets
              .update(_.snippetId === snippetId.url)
              .set(
                _.scalaJsContent := jsContent
              )
              .set(
                _.scalaJsSourceMapContent := jsSourceMap
              )
          )
          val progressId = insertProgress(convertedProgress)
          progress.instrumentations.foreach { instr =>
            val convertedInstr = toPostgresInstrumentation(progressId, instr)
            db.run(PostgresInstrumentations.insert.values(convertedInstr))
          }
          progress.compilationInfos.foreach { info =>
            val convertedInfo = toPostgresCompilationInfo(progressId, info)
            db.run(PostgresCompilationInfos.insert.values(convertedInfo))
          }
          progress.userOutput.foreach { output =>
            val convertedOutput = toPostgresUserOutput(progressId, output)
            db.run(PostgresUserOutputs.insert.values(convertedOutput))
          }
          progress.buildOutput.foreach { output =>
            val convertedOutput = toPostgresBuildOutput(progressId, output)
            db.run(PostgresBuildOutputs.insert.values(convertedOutput))
          }
        }
      case None =>
    }
    Future.unit
  }

  override protected def hideFromUserProfile(snippetId: SnippetId): Future[Unit] = {
    db.transaction { db =>
      db.run(
        PostgresSnippets.update(_.snippetId === snippetId.url).set(_.isShowingInUserProfile := false)
      )
    }
    Future.unit
  }

  def listSnippets(user: UserLogin): Future[List[SnippetSummary]] = Future {
    db.transaction { db =>
      val snippets = db
        .run(
          PostgresSnippets.select
            .filter(_.username === user.login)
            .filter(_.isShowingInUserProfile === true)
        )
        .toList

      /* Get inputs for these snippets (hash -> input) */
      val inputsByHash: Map[String, PostgresInputs[Sc]] = {
        val hashes = snippets.map(_.inputsHash).distinct
        db.run(
          PostgresInputs.select
            .filter(i => hashes.map(h => i.hash === h).reduceOption(_ || _).getOrElse(false))
        ).toList
          .map(i => i.hash -> i)
          .toMap
      }

      /* Create snippet summaries */
      snippets
        .sortBy(-_.time)
        .map { s =>
          val code = inputsByHash.get(s.inputsHash).map(_.code).getOrElse("")
          SnippetSummary(
            SnippetId.fromString(s.snippetId),
            code.split('\n').take(3).mkString("\n"),
            s.time
          )
        }
    }
  }

  def readSnippet(id: SnippetId): Future[Option[FetchResult]] = Future {
    readPostgresSnippet(id).map(_.toFetchResult)
  }

  def readScalaJs(snippetId: SnippetId): Future[Option[FetchResultScalaJs]] = Future {
    readPostgresSnippet(snippetId).map(m => FetchResultScalaJs(m.scalaJsContent))
  }

  def readScalaJsSourceMap(snippetId: SnippetId): Future[Option[FetchResultScalaJsSourceMap]] = Future {
    readPostgresSnippet(snippetId).map(m => FetchResultScalaJsSourceMap(m.scalaJsSourceMapContent))
  }

  def readPostgresSnippet(id: SnippetId): Option[Snippet] = {
    db.transaction { db =>
      val fetchedSnippet = db.run(
        PostgresSnippets.select
          .filter(_.snippetId === id.url)
      )
      if (fetchedSnippet.isEmpty) return None
      val fetchedInput = db.run(
        PostgresInputs.select
          .filter(_.hash === fetchedSnippet.head.inputsHash)
      )
      if (fetchedInput.isEmpty) return None

      val convertedProgress = readProgresses(id)

      val convertedInputs = fromPostgresInputs(
        fetchedInput.head,
        fetchedSnippet.head.isShowingInUserProfile,
        fetchedSnippet.head.forkedSnippetId.map(SnippetId.fromString)
      )

      if (convertedInputs.isEmpty) return None

      fromPostgresSnippet(
        fetchedSnippet.head,
        convertedInputs.head,
        convertedProgress
      ).some
    }
  }

  def delete(snippetId: SnippetId): Future[Boolean] = {
    db.transaction { db =>
      val snippetOpt = db
        .run(
          PostgresSnippets.select
            .filter(_.snippetId === snippetId.url)
        )
        .headOption

      snippetOpt match {
        case None => Future(false)

        case Some(snippet) =>
          /* Delete the snippet itself */
          db.run(PostgresSnippets.delete(_.snippetId === snippetId.url))

          val stillUsed = db.run(
            PostgresSnippets.select
              .filter(_.inputsHash === snippet.inputsHash)
              .size
          )

          /* Delete inputs if no other snippet is using them */
          if (stillUsed == 0) {
            db.run(PostgresInputs.delete(_.hash === snippet.inputsHash))
          }

          Future(true)
      }
    }
  }

}
