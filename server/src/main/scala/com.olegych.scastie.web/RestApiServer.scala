package com.olegych.scastie
package web

import api._
import balancer._
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, Scheduler}
import akka.util.Timeout
import akka.http.scaladsl.model.RemoteAddress
import com.olegych.scastie.util.FormatReq

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class RestApiServer(
    dispatchActor: ActorRef[DispatchActor.Message],
    ip: RemoteAddress,
    maybeUser: Option[User]
)(implicit scheduler: Scheduler)
    extends RestApi {

  implicit val timeout: Timeout = Timeout(20.seconds)

  private def wrap(inputs: Inputs): InputsWithIpAndUser =
    InputsWithIpAndUser(inputs, UserTrace(ip.toString, maybeUser))

  def run(inputs: Inputs): Future[SnippetId] =
    dispatchActor
      .ask(RunSnippet(_, wrap(inputs)))

  def format(formatRequest: FormatRequest): Future[FormatResponse] =
    dispatchActor
      .ask(FormatReq(_, formatRequest))

  def save(inputs: Inputs): Future[SnippetId] =
    dispatchActor
      .ask(SaveSnippet(_, wrap(inputs)))

  def update(editInputs: EditInputs): Future[Option[SnippetId]] = {
    import editInputs._
    if (snippetId.isOwnedBy(maybeUser)) {
      dispatchActor
        .ask(UpdateSnippet(_, snippetId, wrap(inputs)))
    } else {
      Future.successful(None)
    }
  }

  def delete(snippetId: SnippetId): Future[Boolean] = {
    if (snippetId.isOwnedBy(maybeUser)) {
      dispatchActor
        .ask(DeleteSnippet(_, snippetId))
    } else {
      Future.successful(false)
    }
  }

  def fork(editInputs: EditInputs): Future[Option[SnippetId]] = {
    import editInputs._
    dispatchActor
      .ask(ForkSnippet(_, snippetId, wrap(inputs)))
      .mapTo[Option[SnippetId]]
  }

  def fetch(snippetId: SnippetId): Future[Option[FetchResult]] =
    dispatchActor
      .ask(FetchSnippet(_, snippetId))

  def fetchOld(id: Int): Future[Option[FetchResult]] =
    dispatchActor
      .ask(FetchOldSnippet(_, id))

  def fetchUser(): Future[Option[User]] = {
    Future.successful(maybeUser)
  }

  def fetchUserSnippets(): Future[List[SnippetSummary]] = {
    maybeUser match {
      case Some(user) =>
        dispatchActor
          .ask(FetchUserSnippets(_, user))
      case _ => Future.successful(Nil)
    }
  }
}
