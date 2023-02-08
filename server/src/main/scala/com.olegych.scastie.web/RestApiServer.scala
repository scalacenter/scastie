package com.olegych.scastie
package web

import api._
import balancer._

import akka.pattern.ask
import akka.actor.ActorRef
import akka.util.Timeout
import akka.http.scaladsl.model.RemoteAddress

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.DurationInt
import com.olegych.scastie.storage.PolicyAcceptance

class RestApiServer(
    dispatchActor: ActorRef,
    ip: RemoteAddress,
    maybeUser: Option[User]
)(implicit executionContext: ExecutionContext)
    extends RestApi {

  implicit val timeout: Timeout = Timeout(20.seconds)

  private def wrap(inputs: Inputs): InputsWithIpAndUser =
    InputsWithIpAndUser(inputs, UserTrace(ip.toString, maybeUser))

  def run(inputs: Inputs): Future[SnippetId] = {
    dispatchActor
      .ask(RunSnippet(wrap(inputs)))
      .mapTo[SnippetId]
  }

  def format(formatRequest: FormatRequest): Future[FormatResponse] = {
    dispatchActor
      .ask(formatRequest)
      .mapTo[FormatResponse]
  }

  def save(inputs: Inputs): Future[SnippetId] = {
    dispatchActor
      .ask(SaveSnippet(wrap(inputs)))
      .mapTo[SnippetId]
  }

  def update(editInputs: EditInputs): Future[Option[SnippetId]] = {
    import editInputs._
    if (snippetId.isOwnedBy(maybeUser)) {
      dispatchActor
        .ask(UpdateSnippet(snippetId, wrap(inputs)))
        .mapTo[Option[SnippetId]]
    } else {
      Future.successful(None)
    }
  }

  def delete(snippetId: SnippetId): Future[Boolean] = {
    if (snippetId.isOwnedBy(maybeUser)) {
      dispatchActor
        .ask(DeleteSnippet(snippetId))
        .mapTo[Unit]
        .map(_ => true)
    } else {
      Future.successful(false)
    }
  }

  def fork(editInputs: EditInputs): Future[Option[SnippetId]] = {
    import editInputs._
    dispatchActor
      .ask(ForkSnippet(snippetId, wrap(inputs)))
      .mapTo[Option[SnippetId]]
  }

  def fetch(snippetId: SnippetId): Future[Option[FetchResult]] = {
    dispatchActor
      .ask(FetchSnippet(snippetId))
      .mapTo[Option[FetchResult]]
  }

  def fetchOld(id: Int): Future[Option[FetchResult]] = {
    dispatchActor
      .ask(FetchOldSnippet(id))
      .mapTo[Option[FetchResult]]
  }

  def fetchUser(): Future[Option[User]] = {
    Future.successful(maybeUser)
  }

  def fetchUserSnippets(): Future[List[SnippetSummary]] = {
    maybeUser match {
      case Some(user) =>
        dispatchActor
          .ask(FetchUserSnippets(user))
          .mapTo[List[SnippetSummary]]
      case _ => Future.successful(Nil)
    }
  }

  @deprecated("Scheduled for removal", "2023-04-30")
  def getPrivacyPolicy(): Future[Boolean] = {
    maybeUser match {
      case Some(user) =>
        dispatchActor
          .ask(GetPrivacyPolicy(user))
          .mapTo[Boolean]
      case _ => Future.successful(true)
    }
  }

  @deprecated("Scheduled for removal", "2023-04-30")
  def acceptPrivacyPolicy(): Future[Boolean] = {
    maybeUser match {
      case Some(user) =>
        dispatchActor
          .ask(SetPrivacyPolicy(user, true))
          .mapTo[Boolean]
      case _ => Future.successful(true)
    }
  }

  @deprecated("Scheduled for removal", "2023-04-30")
  def removeUserFromPolicyStatus(): Future[Boolean] = {
    maybeUser match {
      case Some(user) =>
        dispatchActor
          .ask(RemovePrivacyPolicy(user))
          .mapTo[Boolean]
      case _ => Future.successful(true)
    }
  }

  @deprecated("Scheduled for removal", "2023-04-30")
  def removeAllUserSnippets(): Future[Boolean] = {
    maybeUser match {
      case Some(user) =>
        dispatchActor
          .ask(RemoveAllUserSnippets(user))
          .mapTo[Boolean]
      case _ => Future.successful(true)
    }
  }
}
