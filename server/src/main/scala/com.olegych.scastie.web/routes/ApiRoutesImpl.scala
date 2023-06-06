package com.olegych.scastie.web.routes

import akka.actor.{ActorRef, ActorSystem}
import com.olegych.scastie.web._
import scastie.endpoints.ApiEndpoints
import scala.concurrent.Future
import cats.syntax.all._
import akka.http.scaladsl.model.RemoteAddress

class ApiRoutesImpl(dispatchActor: ActorRef)(implicit system: ActorSystem) {
  import system.dispatcher
  import SessionManager._

  val saveImpl = ApiEndpoints.saveEndpoint
    .secure
    .serverLogicSuccess(maybeUser =>
      new RestApiServer(dispatchActor, RemoteAddress.Unknown, maybeUser).save(_)
    )

  val forkImpl =
    ApiEndpoints.forkEndpoint
    .secure
    .serverLogic(maybeUser =>
      new RestApiServer(dispatchActor, RemoteAddress.Unknown, maybeUser).fork(_).map(_.toRight("Failure"))
    )

  val deleteImpl =
    ApiEndpoints.deleteEndpoint
    .secure
    .serverLogicSuccess(user =>
      new RestApiServer(dispatchActor, RemoteAddress.Unknown, Some(user)).delete(_)
    )

  val updateImpl =
    ApiEndpoints.updateEndpoint
    .secure
    .serverLogic(user =>
      new RestApiServer(dispatchActor, RemoteAddress.Unknown, Some(user)).update(_).map(_.toRight("Failure"))
    )

  val userSettingsImpl = ApiEndpoints.userSettingsEndpoint.secure
    .serverLogic(user => _ => Future(user.asRight))

  val userSnippetsEndpoint = ApiEndpoints.userSnippetsEndpoint.secure
    .serverLogicSuccess(user => _ =>
        new RestApiServer(dispatchActor, RemoteAddress.Unknown, Some(user)).fetchUserSnippets()
    )

  val serverEndpoints = List(saveImpl, forkImpl, deleteImpl, updateImpl, userSettingsImpl, userSnippetsEndpoint)
}
