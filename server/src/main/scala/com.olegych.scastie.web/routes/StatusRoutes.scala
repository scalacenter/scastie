package com.olegych.scastie
package web
package routes

import api._
import balancer._
import oauth2.UserDirectives

import de.heikoseeberger.akkasse.scaladsl.model.ServerSentEvent
import de.heikoseeberger.akkasse.scaladsl.marshalling.EventStreamMarshalling._

import akka.util.Timeout
import akka.NotUsed
import akka.actor.ActorRef
import akka.pattern.ask

import akka.http.scaladsl._
import server._
import server.Directives._
import akka.stream.scaladsl._

import upickle.default.{write => uwrite}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration._

import scala.collection.immutable.Queue


class StatusRoutes(statusActor: ActorRef, 
                   userDirectives: UserDirectives) {

  implicit val timeout = Timeout(1.seconds)

  val adminUser: Directive1[Boolean] =
    userDirectives.userLogin.map(user =>
      user.map(_.isAdmin).getOrElse(false)
    )

  def hideTask(isAdmin: Boolean, progress: StatusProgress): StatusProgress =
    if(isAdmin) progress
    else
      progress match {
        case StatusInfo(runners) =>
          // Hide the task Queue for non admin users, they will only see the runner count
          StatusInfo(runners.map(_.copy(tasks = Queue())))

        case _ =>
          progress
      }

  val routes =
    path("status-sse")(
      adminUser(isAdmin =>
        complete(
          statusSource().map(progress => ServerSentEvent(
            uwrite(progress)
          ))
        )
      )
    )

  private def statusSource() = {
    // TODO find a way to flatten Source[Source[T]]
    Await.result(
      (statusActor ? SubscribeStatus)
        .mapTo[Source[StatusProgress, NotUsed]],
      1.second
    )
  }
}
