package com.olegych.scastie.storage.mongodb

import com.olegych.scastie.api._
import com.olegych.scastie.storage._
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._

import java.lang.System.{lineSeparator => nl}
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._


trait MongoDBSnippetsContainer extends SnippetsContainer with GenericMongoContainer {
  lazy val snippets = {
    val db = database.getCollection[Document]("snippets")

    Await.result(db.createIndex(Indexes.ascending("simpleSnippetId", "oldId"), IndexOptions().unique(true)).head(), Duration.Inf)
    Await.result(Future.sequence(Seq(
      Indexes.hashed("simpleSnippetId"),
      Indexes.hashed("oldId"),
      Indexes.hashed("user"),
      Indexes.hashed("snippetId.user.login"),
      Indexes.hashed("inputs.isShowingInUserProfile"),
      Indexes.hashed("time")
    ).map(db.createIndex(_).head())), Duration.Inf)

    db
  }


  def toMongoSnippet(snippetId: SnippetId, inputs: Inputs): MongoSnippet = MongoSnippet(
    simpleSnippetId = snippetId.url,
    user = snippetId.user.map(_.login),
    snippetId = snippetId,
    oldId = 0,
    inputs = inputs,
    progresses = Nil,
    scalaJsContent = "",
    scalaJsSourceMapContent = "",
    time = System.currentTimeMillis
  )

  protected def insert(snippetId: SnippetId, inputs: Inputs): Future[Unit] = {
    val snippet = toBson(toMongoSnippet(snippetId, inputs.withSavedConfig))
    snippets.insertOne(snippet).toFuture().map(_ => ())
  }

  override protected def hideFromUserProfile(snippetId: SnippetId): Future[Unit] =
    snippets.updateOne(select(snippetId), set("inputs.isShowingInUserProfile", false)).headOption().map(_ => ())

  private def select(snippetId: SnippetId) = Document("simpleSnippetId" -> snippetId.url)

  def delete(snippetId: SnippetId): Future[Boolean] =
    snippets.deleteOne(select(snippetId)).map(_.wasAcknowledged).headOption().map(_.getOrElse(false))

  def appendOutput(progress: SnippetProgress): Future[Unit] = {
    progress.snippetId match {
      case Some(snippetId) =>
        val selection = select(snippetId)

        val appendOutputLogs = {
          val update = push("progresses", toBson(progress))
          snippets.updateOne(selection, update).map(_.wasAcknowledged).headOption()
        }

        val setScalaJsOutput = (progress.scalaJsContent, progress.scalaJsSourceMapContent) match {
          case (Some(scalaJsContent), Some(scalaJsSourceMapContent)) =>
            val updateJs =
              combine(set("scalaJsContent", scalaJsContent), set("scalaJsSourceMapContent", scalaJsSourceMapContent))
            snippets.updateOne(selection, updateJs.toBsonDocument).map(_.wasAcknowledged).headOption()
          case _ => Future(())
        }

        appendOutputLogs.zip(setScalaJsOutput).map(_ => ())
      case None => Future(())
    }
  }

  def readMongoSnippet(snippetId: SnippetId): Future[Option[MongoSnippet]] = {
    snippets
      .find(select(snippetId))
      .first()
      .headOption()
      .map(_.flatMap(fromBson[MongoSnippet]))
  }

  def readSnippet(snippetId: SnippetId): Future[Option[FetchResult]] = {
    readMongoSnippet(snippetId).map(_.map(_.toFetchResult))
  }

  def listSnippets(user: UserLogin): Future[List[SnippetSummary]] = {
    val userSnippets = and(
      Document(
        "snippetId.user.login"          -> user.login,
        "inputs.isShowingInUserProfile" -> true
      )
    )

    val mongoSnippets = snippets
      .find(userSnippets)
      .projection(
        Document(
          "snippetId"     -> 1,
          "inputs.code"   -> 1,
          "inputs.target" -> 1,
          "time"          -> 1
        )
      )
      .map(fromBson[ShortMongoSnippet])

    mongoSnippets
      .collect()
      .headOption()
      .map(results => {
        val sortedSnippets = results.getOrElse(Nil).flatten.sortBy(-_.time)
        sortedSnippets
          .map(result =>
            SnippetSummary(result.snippetId, result.inputs.code.split(nl).take(3).mkString(nl), result.time)
          )
          .toList
      })
  }

  def readScalaJs(snippetId: SnippetId): Future[Option[FetchResultScalaJs]] = readMongoSnippet(snippetId).map(
    _.map(m => FetchResultScalaJs(m.scalaJsContent))
  )

  def readScalaJsSourceMap(
    snippetId: SnippetId
  ): Future[Option[FetchResultScalaJsSourceMap]] = readMongoSnippet(snippetId).map(
    _.map(m => FetchResultScalaJsSourceMap(m.scalaJsSourceMapContent))
  )

  def readOldSnippet(id: Int): Future[Option[FetchResult]] = snippets
    .find(Document("oldId" -> id))
    .first()
    .headOption()
    .map(_.flatMap(fromBson[MongoSnippet]).map(_.toFetchResult))

  override def removeUserSnippets(user: UserLogin): Future[Boolean] = {
    val query = or(Document("user" -> user.login), Document("snippetId.user.login" -> user.login))
    val deletion = snippets.deleteMany(query).head().map(_.wasAcknowledged)

    lazy val validation = listSnippets(user).map(_.isEmpty)
    deletion.flatMap(deletionResult => validation.map(_ && deletionResult))
  }

  override def close(): Unit = client.close()
}
