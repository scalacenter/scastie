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
    user: User)(implicit timeout: Timeout, executionContext: ExecutionContext)
    extends Api {

  def run(inputs: Inputs): Future[SnippetId] = {
    (dispatchActor ? InputsWithUser(
      inputs,
      ip.toString,
      Some(user.login)
    )).mapTo[SnippetId]
  }

  def save(inputs: Inputs): Future[SnippetId] = run(inputs)

  def fetch(snippetId: SnippetId): Future[Option[FetchResult]] = {
    (dispatchActor ? GetSnippet(snippetId)).mapTo[Option[FetchResult]]
  }

  def format(formatRequest: FormatRequest): Future[FormatResponse] = {
    (dispatchActor ? formatRequest).mapTo[FormatResponse]
  }

  // eventually no login will be required
  def fetchUser(): Future[Option[User]] = 
    Future.successful(Some(user))

  def fetchUserSnippets(): Future[List[SnippetSummary]] = {
    (dispatchActor ? GetUserSnippets(user)).mapTo[List[SnippetSummary]]
  }

  def delete(snippetId: SnippetId): Future[Unit] = {
    (dispatchActor ? DeleteSnippet(snippetId)).mapTo[Unit]
  }
}
