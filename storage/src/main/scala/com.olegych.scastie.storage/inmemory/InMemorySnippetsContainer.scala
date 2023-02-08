package com.olegych.scastie.storage.inmemory

import com.olegych.scastie.api._
import scala.concurrent.{ExecutionContext, Future}

import scala.collection.mutable
import System.{lineSeparator => nl}
import com.olegych.scastie.storage.SnippetsContainer
import com.olegych.scastie.storage.UserLogin
import com.olegych.scastie.storage.UsersContainer
import com.olegych.scastie.storage.PolicyAcceptance


trait InMemorySnippetsContainer extends SnippetsContainer {

  private val snippets = mutable.Map[SnippetId, Storage]()

  case class Storage(
      snippetId: SnippetId,
      inputs: Inputs,
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

  def readOldSnippet(id: Int): Future[Option[FetchResult]] = Future(None)

  protected def insert(snippetId: SnippetId, inputs: Inputs): Future[Unit] =
    Future {
      snippets.update(snippetId, Storage(snippetId, inputs.withSavedConfig))
    }

  override protected def hideFromUserProfile(snippetId: SnippetId): Future[Unit] = Future {
    for {
      old <- snippets.get(snippetId)
    } yield snippets.update(snippetId, old.copy(inputs = old.inputs.copy(isShowingInUserProfile = false)))
  }
}
