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

  def run(inputs: Inputs): Future[Ressource] = {
    (dispatchActor ? InputsWithUser(
      inputs,
      ip.toString,
      user.login
    )).mapTo[Ressource]
  }

  def save(inputs: Inputs): Future[Ressource] = run(inputs)

  def fetch(id: Int): Future[Option[FetchResult]] = {
    (dispatchActor ? GetPaste(id)).mapTo[Option[FetchResult]]
  }

  def format(formatRequest: FormatRequest): Future[FormatResponse] = {
    (dispatchActor ? formatRequest).mapTo[FormatResponse]
  }

  // eventually no login will be required
  def fetchUser(): Future[Option[User]] = 
    Future.successful(Some(user))
}
