package com.olegych.scastie
package web

import api._
import balancer._

import akka.pattern.ask
import akka.actor.ActorRef
import akka.util.Timeout
import akka.http.scaladsl.model.RemoteAddress

import scala.concurrent.{Future, ExecutionContext}

class ApiImplementation(
    dispatchActor: ActorRef,
    ip: RemoteAddress,
    maybeUser: Option[User])(implicit timeout: Timeout, executionContext: ExecutionContext)
    extends Api {

  def run(inputs: Inputs): Future[SnippetId] = {
    (dispatchActor ? RunSnippet(
      inputs,
      ip.toString,
      maybeUser
    )).mapTo[SnippetId]
  }

  def format(formatRequest: FormatRequest): Future[FormatResponse] = {
    (dispatchActor ? formatRequest).mapTo[FormatResponse]
  }

  def create(inputs: Inputs): Future[SnippetId] = {
    (dispatchActor ? CreateSnippet(inputs, maybeUser)).mapTo[SnippetId]
  }

  def amend(snippetId: SnippetId, inputs: Inputs): Future[Boolean] = {
    if(userOwnsSnippet(snippetId)) {
      (dispatchActor ? AmendSnippet(snippetId, inputs)).mapTo[Boolean]
    } else {
      Future.successful(false)
    }
  }

  def update(snippetId: SnippetId, inputs: Inputs): Future[Option[SnippetId]] = {
    if(userOwnsSnippet(snippetId)) {
      (dispatchActor ? UpdateSnippet(snippetId, inputs)).mapTo[SnippetId].map(Some.apply)
    }
    else {
      Future.successful(None)
    }
  }

  def delete(snippetId: SnippetId): Future[Boolean] = {
    if(userOwnsSnippet(snippetId)) {
      (dispatchActor ? DeleteSnippet(snippetId)).mapTo[Unit].map(_ => true)
    } else {
      Future.successful(false)
    }
  }

  def fork(snippetId: SnippetId): Future[Option[ForkResult]] = {
    (dispatchActor ? ForkSnippet(snippetId, maybeUser)).mapTo[Option[ForkResult]]
  }

  def fetch(snippetId: SnippetId): Future[Option[FetchResult]] = {
    (dispatchActor ? FetchSnippet(snippetId)).mapTo[Option[FetchResult]]
  }

  def fetchUser(): Future[Option[User]] = 
    Future.successful(maybeUser)

  def fetchUserSnippets(): Future[List[SnippetSummary]] = {
    maybeUser match {
      case Some(user) => (dispatchActor ? FetchUserSnippets(user)).mapTo[List[SnippetSummary]]
      case _ => Future.successful(Nil)
    }
  }

  private def userOwnsSnippet(snippetId: SnippetId): Boolean = {
    (snippetId.user, maybeUser) match {
      case (Some(SnippetUserPart(snippetLogin, _)), Some(User(userLogin, _, _))) => 
        snippetLogin == userLogin
      case _ => false
    }
  }
}
