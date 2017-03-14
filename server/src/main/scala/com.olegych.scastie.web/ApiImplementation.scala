package com.olegych.scastie
package web

import api._
import balancer._

import akka.pattern.ask
import akka.actor.ActorRef
import akka.util.Timeout
import akka.http.scaladsl.model.RemoteAddress

import scala.concurrent.{Future, ExecutionContext}

class ApiImplementation(dispatchActor: ActorRef,
                        ip: RemoteAddress,
                        maybeUser: Option[User])(
    implicit timeout: Timeout,
    executionContext: ExecutionContext)
    extends Api {

  private def wrap(inputs: Inputs): InputsWithIpAndUser =
    InputsWithIpAndUser(inputs, ip.toString, maybeUser)

  def run(inputs: Inputs): Future[SnippetId] = {
    (dispatchActor ? RunSnippet(wrap(inputs))).mapTo[SnippetId]
  }

  def format(formatRequest: FormatRequest): Future[FormatResponse] = {
    (dispatchActor ? formatRequest).mapTo[FormatResponse]
  }

  def save(inputs: Inputs): Future[SnippetId] = {
    (dispatchActor ? SaveSnippet(wrap(inputs))).mapTo[SnippetId]
  }

  def amend(snippetId: SnippetId, inputs: Inputs): Future[Boolean] = {
    if (userOwnsSnippet(snippetId)) {
      (dispatchActor ? AmendSnippet(snippetId, wrap(inputs))).mapTo[Boolean]
    } else {
      Future.successful(false)
    }
  }

  def update(snippetId: SnippetId, inputs: Inputs): Future[Option[SnippetId]] = {
    if (userOwnsSnippet(snippetId)) {
      (dispatchActor ? UpdateSnippet(snippetId, wrap(inputs)))
        .mapTo[Option[SnippetId]]
    } else {
      Future.successful(None)
    }
  }

  def delete(snippetId: SnippetId): Future[Boolean] = {
    if (userOwnsSnippet(snippetId)) {
      (dispatchActor ? DeleteSnippet(snippetId)).mapTo[Unit].map(_ => true)
    } else {
      Future.successful(false)
    }
  }

  def fork(snippetId: SnippetId, inputs: Inputs): Future[Option[SnippetId]] = {
    (dispatchActor ? ForkSnippet(snippetId, wrap(inputs)))
      .mapTo[Option[SnippetId]]
  }

  def fetch(snippetId: SnippetId): Future[Option[FetchResult]] = {
    (dispatchActor ? FetchSnippet(snippetId)).mapTo[Option[FetchResult]]
  }

  def fetchUser(): Future[Option[User]] =
    Future.successful(maybeUser)

  def fetchUserSnippets(): Future[List[SnippetSummary]] = {
    maybeUser match {
      case Some(user) =>
        (dispatchActor ? FetchUserSnippets(user)).mapTo[List[SnippetSummary]]
      case _ => Future.successful(Nil)
    }
  }

  private def userOwnsSnippet(snippetId: SnippetId): Boolean = {
    (snippetId.user, maybeUser) match {
      case (Some(SnippetUserPart(snippetLogin, _)),
            Some(User(userLogin, _, _))) =>
        snippetLogin == userLogin
      case _ => false
    }
  }
}
