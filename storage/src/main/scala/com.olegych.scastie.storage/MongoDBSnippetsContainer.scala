package com.olegych.scastie.storage

import java.lang.System.{lineSeparator => nl}

import com.olegych.scastie.api._
import play.api.libs.json.{Json, OFormat}
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{AsyncDriver, Cursor, MongoConnection}
import reactivemongo.play.json.compat._

import scala.concurrent.{ExecutionContext, Future}

sealed trait BaseMongoSnippet {
  def snippetId: SnippetId
  def inputs: BaseInputs
  def time: Long
}

case class ShortMongoSnippet(
    snippetId: SnippetId,
    inputs: ShortInputs,
    time: Long
) extends BaseMongoSnippet

object ShortMongoSnippet {
  implicit val formatShortMongoSnippet: OFormat[ShortMongoSnippet] = Json.format[ShortMongoSnippet]
}

case class MongoSnippet(
    simpleSnippetId: String,
    user: Option[String],
    snippetId: SnippetId,
    oldId: Long,
    inputs: Inputs,
    progresses: List[SnippetProgress],
    scalaJsContent: String,
    scalaJsSourceMapContent: String,
    time: Long
) extends BaseMongoSnippet {
  def toFetchResult: FetchResult = FetchResult.create(inputs, progresses)
}

object MongoSnippet {
  implicit val formatMongoSnippet: OFormat[MongoSnippet] = Json.format[MongoSnippet]
}

class MongoDBSnippetsContainer(_ec: ExecutionContext) extends SnippetsContainer {
  protected implicit val ec: ExecutionContext = _ec

  private val mongoUri = "mongodb://localhost:27017/snippets"
  private val driver = AsyncDriver()
  private val connection = for {
    parsedUri <- MongoConnection.fromString(mongoUri)
    connection <- driver.connect(parsedUri)
  } yield connection
  private def snippets =
    for {
      connection <- connection
      database <- connection.database("snippets")
    } yield database.collection[BSONCollection]("snippets")
  private val initDatabase = {
    val snippetIdIndex = Index(key = Seq(("simpleSnippetId", IndexType.Hashed)), name = Some("snippets-id"))
    val oldSnippetIdIndex = Index(key = Seq(("oldId", IndexType.Hashed)), name = Some("snippets-old-id"))
    val userIndex = Index(key = Seq(("user", IndexType.Hashed)), name = Some("user"))
    val snippetUserId = Index(key = Seq(("snippetId.user.login", IndexType.Hashed)), name = Some("snippetId.user.login"))
    val isShowingInUserProfileIndex = Index(
      key = Seq(("inputs.isShowingInUserProfile", IndexType.Hashed)),
      name = Some("inputs.isShowingInUserProfile")
    )
    val timeIndex = Index(key = Seq(("time", IndexType.Hashed)), name = Some("time"))
    for {
      coll <- snippets
      _ <- Future.traverse(
        List(snippetIdIndex, oldSnippetIdIndex, userIndex, snippetUserId, isShowingInUserProfileIndex, timeIndex)
      ) { i =>
        coll.indexesManager.ensure(i)
      }
    } yield ()
  }

  def toMongoSnippet(snippetId: SnippetId, inputs: Inputs): MongoSnippet =
    MongoSnippet(
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

  def isSuccess(writeResult: WriteResult): Boolean =
    if (writeResult.ok) true
    else throw new Exception(writeResult.toString)

  protected def insert(snippetId: SnippetId, inputs: Inputs): Future[Unit] = {
    snippets.flatMap(_.insert.one(toMongoSnippet(snippetId, inputs.withSavedConfig))).map(r => isSuccess(r))
  }

  override protected def hideFromUserProfile(snippetId: SnippetId): Future[Unit] =
    snippets.flatMap(
      _.update
        .one(select(snippetId),
             Json.obj(
               op("set") -> Json.obj(
                 "inputs.isShowingInUserProfile" -> false,
               )
             ))
        .map(_ => ())
    )

  private def select(snippetId: SnippetId) = Json.obj("simpleSnippetId" -> snippetId.url)

  def delete(snippetId: SnippetId): Future[Boolean] = {
    snippets.flatMap(_.delete().one(select(snippetId)).map(isSuccess))
  }

  private def op(o: String): String = f"$$" + o

  def appendOutput(progress: SnippetProgress): Future[Unit] = {
    progress.snippetId match {
      case Some(snippetId) =>
        val selection = select(snippetId)

        val appendOutputLogs = {
          val update = Json.obj(
            op("push") -> Json.obj(
              "progresses" -> progress
            )
          )
          snippets.flatMap(_.update.one(selection, update).map(isSuccess))
        }

        val setScalaJsOutput =
          (progress.scalaJsContent, progress.scalaJsSourceMapContent) match {
            case (Some(scalaJsContent), Some(scalaJsSourceMapContent)) =>
              val updateJs = Json.obj(
                op("set") -> Json.obj(
                  "scalaJsContent" -> scalaJsContent,
                  "scalaJsSourceMapContent" -> scalaJsSourceMapContent
                )
              )
              snippets.flatMap(_.update.one(selection, updateJs).map(isSuccess))
            case _ => Future(())
          }

        appendOutputLogs.zip(setScalaJsOutput).map(_ => ())
      case None => Future(())
    }
  }

  def readMongoSnippet(snippetId: SnippetId): Future[Option[MongoSnippet]] =
    snippets.flatMap(_.find(select(snippetId), Some(Json.obj())).one[MongoSnippet])

  def readSnippet(snippetId: SnippetId): Future[Option[FetchResult]] =
    readMongoSnippet(snippetId).map(_.map(_.toFetchResult))

  def listSnippets(user: UserLogin): Future[List[SnippetSummary]] = {
    val userSnippets = Json.obj(
      "$and" -> List(
        Json.obj(
          "snippetId.user.login" -> user.login,
          "inputs.isShowingInUserProfile" -> true,
        )
      )
    )

    val mongoSnippets =
      snippets.flatMap(
        _.find(userSnippets,
               Some(
                 Json.obj(
                   "snippetId" -> 1,
                   "inputs.code" -> 1,
                   "inputs.target" -> 1,
                   "time" -> 1
                 )
               ))
          .cursor[ShortMongoSnippet]()
          .collect[List](maxDocs = 1000, Cursor.FailOnError[List[ShortMongoSnippet]]())
      )

    mongoSnippets.map(
      _.sortBy(-_.time).map(
        m =>
          SnippetSummary(
            m.snippetId,
            m.inputs.code.split(nl).take(3).mkString(nl),
            m.time
        )
      )
    )
  }

  def readScalaJs(snippetId: SnippetId): Future[Option[FetchResultScalaJs]] =
    readMongoSnippet(snippetId).map(
      _.map(m => FetchResultScalaJs(m.scalaJsContent))
    )

  def readScalaJsSourceMap(
      snippetId: SnippetId
  ): Future[Option[FetchResultScalaJsSourceMap]] =
    readMongoSnippet(snippetId).map(
      _.map(m => FetchResultScalaJsSourceMap(m.scalaJsSourceMapContent))
    )

  private def select(id: Int) = Json.obj("oldId" -> id)

  def readOldSnippet(id: Int): Future[Option[FetchResult]] =
    snippets.flatMap(
      _.find(select(id), Some(Json.obj())).one[MongoSnippet].map(_.map(_.toFetchResult))
    )

  override def close(): Unit = driver.close()
}
