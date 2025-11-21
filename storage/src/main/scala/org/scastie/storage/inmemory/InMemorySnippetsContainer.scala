package org.scastie.storage.inmemory

import org.scastie.api._
import org.scastie.storage.SnippetsContainer
import org.scastie.storage.UserLogin

import scala.collection.mutable
import scala.concurrent.Future

import System.{lineSeparator => nl}


trait InMemorySnippetsContainer extends SnippetsContainer {

  private val snippets = mutable.Map[SnippetId, Storage]()

  case class Storage(
      snippetId: SnippetId,
      inputs: BaseInputs,
      progresses: mutable.Queue[SnippetProgress] = mutable.Queue(),
      var scalaJsContent: String = "",
      var scalaJsSourceMapContent: String = "",
      time: Long = System.currentTimeMillis
  )

  def appendOutput(progress: SnippetProgress): Future[Unit] = Future {
    progress.snippetId.foreach(
      id => snippets.get(id).foreach(storage => storage.progresses += progress)
    )
  }
  def delete(snippetId: SnippetId): Future[Boolean] = Future {
    val found = snippets.contains(snippetId)
    snippets -= snippetId
    found
  }

  def listSnippets(user: UserLogin): Future[List[SnippetSummary]] = Future {
    snippets.view
      .filterKeys(_.user == Some(user.login))
      .values
      .map { m =>
        SnippetSummary(
          m.snippetId,
          m.inputs.code.split(nl).take(3).mkString(nl),
          m.time
        )
      }
      .toList
  }

  def readScalaJs(snippetId: SnippetId): Future[Option[FetchResultScalaJs]] =
    Future {
      snippets.get(snippetId).map(m => FetchResultScalaJs(m.scalaJsContent))
    }

  def readScalaJsSourceMap(
      snippetId: SnippetId
  ): Future[Option[FetchResultScalaJsSourceMap]] = Future {
    snippets
      .get(snippetId)
      .map(m => FetchResultScalaJsSourceMap(m.scalaJsSourceMapContent))
  }

  def readSnippet(snippetId: SnippetId): Future[Option[FetchResult]] = Future {
    snippets.get(snippetId).map(m => FetchResult.create(m.inputs, m.progresses.toList))
  }

  def readLatestSnippet(snippetId: SnippetId): Future[Option[FetchResult]] = Future {
    snippetId.user match {
      case Some(SnippetUserPart(login, _)) =>
        snippets
          .filter { case (id, _) =>
            id.base64UUID == snippetId.base64UUID &&
            id.user.exists(_.login == login)
          }
          .toSeq
          .sortBy { case (id, _) => id.user.map(_.update).getOrElse(0) }
          .lastOption
          .map { case (_, storage) => FetchResult.create(storage.inputs, storage.progresses.toList) }
      case None =>
        snippets.get(snippetId).map(m => FetchResult.create(m.inputs, m.progresses.toList))
    }
  }

  def readOldSnippet(id: Int): Future[Option[FetchResult]] = Future(None)

  protected def insert(snippetId: SnippetId, inputs: BaseInputs): Future[Unit] = {
    val adjustedInputs = inputs match {
      case sbtInputs: SbtInputs => sbtInputs.withSavedConfig
      case _ => inputs
    }
    Future {
      snippets.update(snippetId, Storage(snippetId, adjustedInputs))
    }
  }

  override protected def hideFromUserProfile(snippetId: SnippetId): Future[Unit] = Future {
    for {
      old <- snippets.get(snippetId)
    } yield snippets.update(snippetId, old.copy(inputs = old.inputs.copyBaseInput(isShowingInUserProfile = false)))
  }
}
