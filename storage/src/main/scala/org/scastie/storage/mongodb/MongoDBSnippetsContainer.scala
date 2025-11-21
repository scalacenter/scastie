package org.scastie.storage.mongodb

import com.mongodb.client.result.UpdateResult
import org.mongodb.scala._
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._
import org.scastie.api._
import org.scastie.storage._

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


  def toMongoSnippet(snippetId: SnippetId, inputs: BaseInputs): MongoSnippet = MongoSnippet(
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

  protected def insert(snippetId: SnippetId, inputs: BaseInputs): Future[Unit] = {
    val adjustedInputs = inputs match {
      case sbtInputs: SbtInputs => sbtInputs.withSavedConfig
      case _ => inputs
    }
    val snippet = toBson(toMongoSnippet(snippetId, adjustedInputs))
    snippets.insertOne(snippet).toFuture().map(_ => ())
  }

  def updateSnippet(snippetId: SnippetId)(update: MongoSnippet => MongoSnippet): Future[Option[UpdateResult]] =
    readMongoSnippet(snippetId).flatMap {
      case Some(value) =>
        val updatedSnippet = update(value)
        snippets.replaceOne(select(snippetId), toBson(updatedSnippet)).headOption()
      case None => Future.successful(None)
    }

  override protected def hideFromUserProfile(snippetId: SnippetId): Future[Unit] =
    updateSnippet(snippetId)(oldSnippet =>
      oldSnippet.copy(inputs = oldSnippet.inputs.copyBaseInput(isShowingInUserProfile = false))
    ).map(_ => ())

  private def select(snippetId: SnippetId) = equal("simpleSnippetId", snippetId.url)

  def delete(snippetId: SnippetId): Future[Boolean] =
    snippets.deleteOne(select(snippetId)).map(_.wasAcknowledged).headOption().map(_.getOrElse(false))

  def appendOutput(progress: SnippetProgress): Future[Unit] =
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

  def readLatestSnippet(snippetId: SnippetId): Future[Option[FetchResult]] = {
    snippetId.user match {
      case Some(SnippetUserPart(login, _)) =>
        val query = and(
          equal("snippetId.base64UUID", snippetId.base64UUID),
          equal("snippetId.user.login", login)
        )

        snippets
          .find(query)
          .sort(Sorts.descending("snippetId.user.update"))
          .first()
          .headOption()
          .map(_.flatMap(fromBson[MongoSnippet]).map(_.toFetchResult))
      case None =>
        readSnippet(snippetId)
    }
  }

  def listSnippets(user: UserLogin): Future[List[SnippetSummary]] = {
    val userSnippets = and(
      equal("snippetId.user.login", user.login),
      or(
        equal("inputs.SbtInputs.isShowingInUserProfile", true),
        equal("inputs.ScalaCliInputs.isShowingInUserProfile", true)
      )
    )

    val mongoSnippets = snippets
      .find(userSnippets)
      .projection(
        fields(
          include("snippetId"),
          computed("inputs.code", Document("$ifNull" -> Seq("$inputs.SbtInputs.code", "$inputs.ScalaCliInputs.code"))),
          computed("inputs.target", Document("$ifNull" ->
            BsonArray("$inputs.SbtInputs.target", Document("ScalaCli" -> "$inputs.ScalaCliInputs.target")))
          ),
          include("time")
        ),
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
    .find(equal("oldId", id))
    .first()
    .headOption()
    .map(_.flatMap(fromBson[MongoSnippet]).map(_.toFetchResult))

  override def removeUserSnippets(user: UserLogin): Future[Boolean] = {
    val query = or(
      equal("user", user.login),
      equal("snippetId.user.login", user.login)
    )
    val deletion = snippets.deleteMany(query).head().map(_.wasAcknowledged)

    lazy val validation = listSnippets(user).map(_.isEmpty)
    deletion.flatMap(deletionResult => validation.map(_ && deletionResult))
  }

  override def close(): Unit = client.close()
}
