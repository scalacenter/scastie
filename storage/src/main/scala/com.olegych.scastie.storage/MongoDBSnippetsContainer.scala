package com.olegych.scastie.storage

import com.olegych.scastie.api._
import play.api.libs.json._
import reactivemongo.play.json._
import reactivemongo.play.json.collection._
import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.api.commands.WriteResult

import scala.concurrent.{ExecutionContext, Future}
import System.{lineSeparator => nl}

import reactivemongo.api.indexes.{Index, IndexType}

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
) {
  def toFetchResult: FetchResult = FetchResult.create(inputs, progresses)
}
object MongoSnippet {
  implicit val formatMongoSnippet: OFormat[MongoSnippet] =
    Json.format[MongoSnippet]
}

class MongoDBSnippetsContainer(implicit protected val ec: ExecutionContext) extends SnippetsContainer {

  val mongoUri = "mongodb://localhost:27017/snippets"
  val driver = MongoDriver()
  val parsedUri = MongoConnection.parseURI(mongoUri)
  val connection = parsedUri.map(driver.connection)

  val futureConnection = Future.fromTry(connection)
  val fdb = futureConnection.flatMap(_.database("snippets"))
  val snippets = {
    val snippetIdIndex = Index(key = Seq(("simpleSnippetId", IndexType.Hashed)), name = Some("snippets-id"))
    val oldSnippetIdIndex = Index(key = Seq(("oldId", IndexType.Hashed)), name = Some("snippets-old-id"))
    val userIndex = Index(key = Seq(("user", IndexType.Hashed)), name = Some("user"))
    val snippetUserId = Index(key = Seq(("snippetId.user.login", IndexType.Hashed)), name = Some("snippetId.user.login"))
    val isShowingInUserProfileIndex = Index(
      key = Seq(("inputs.isShowingInUserProfile", IndexType.Hashed)),
      name = Some("inputs.isShowingInUserProfile")
    )
    val timeIndex = Index(key = Seq(("time", IndexType.Hashed)), name = Some("time"))
    val listUserSnippetsIndex =
      Index(key = List(snippetIdIndex, isShowingInUserProfileIndex, timeIndex).flatMap(_.key), name = Some("listUserSnippetsIndex"))
    for {
      fdb <- fdb
      coll = fdb.collection("snippets")
      _ <- Future.traverse(
        List(snippetIdIndex, oldSnippetIdIndex, userIndex, snippetUserId, isShowingInUserProfileIndex, timeIndex, listUserSnippetsIndex)
      ) { i =>
        coll.indexesManager.ensure(i)
      }
    } yield coll
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
    snippets.flatMap(_.insert(toMongoSnippet(snippetId, inputs))).map(isSuccess)
  }

  private def select(snippetId: SnippetId): BSONDocument =
    BSONDocument("simpleSnippetId" -> snippetId.url)

  def delete(snippetId: SnippetId): Future[Boolean] = {
    snippets.flatMap(_.delete().one(select(snippetId)).map(isSuccess))
  }

  private def op(o: String): String = f"$$" + o

  def appendOutput(progress: SnippetProgress): Future[Unit] = {
    progress.snippetId match {
      case Some(snippetId) => {
        val selection = select(snippetId)

        val appendOutputLogs = {
          val update =
            BSONDocument(
              op("push") -> BSONDocument(
                "progresses" -> Json.toJson(progress)
              )
            )

          snippets.flatMap(_.update(selection, update).map(isSuccess))
        }

        val setScalaJsOutput =
          (progress.scalaJsContent, progress.scalaJsSourceMapContent) match {
            case (Some(scalaJsContent), Some(scalaJsSourceMapContent)) =>
              val updateJs = BSONDocument(
                op("set") -> BSONDocument(
                  "scalaJsContent" -> scalaJsContent,
                  "scalaJsSourceMapContent" -> scalaJsSourceMapContent
                )
              )
              snippets.flatMap(_.update(selection, updateJs).map(isSuccess))
            case _ => Future(())
          }

        appendOutputLogs.zip(setScalaJsOutput).map(_ => ())
      }
      case None => Future(())
    }
  }

  def readMongoSnippet(snippetId: SnippetId): Future[Option[MongoSnippet]] =
    snippets.flatMap(_.find(select(snippetId)).one[MongoSnippet])

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
        _.find(userSnippets)
          .sort(Json.obj("time" -> -1))
          .cursor[MongoSnippet]()
          .collect[List](maxDocs = 1000, Cursor.FailOnError[List[MongoSnippet]]())
      )

    mongoSnippets.map(
      _.map(
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

  private def select(id: Int): BSONDocument =
    BSONDocument("oldId" -> id)

  def readOldSnippet(id: Int): Future[Option[FetchResult]] =
    snippets.flatMap(
      _.find(select(id)).one[MongoSnippet].map(_.map(_.toFetchResult))
    )

  override def close(): Unit = driver.close()
}
