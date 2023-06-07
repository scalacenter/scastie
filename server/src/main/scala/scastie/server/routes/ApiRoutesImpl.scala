package scastie.server.routes

import scala.concurrent.Future

import akka.actor.{ActorRef, ActorSystem}
import cats.syntax.all._
import scastie.endpoints.ApiEndpoints
import scastie.server.RestApiServer

class ApiRoutesImpl(dispatchActor: ActorRef)(implicit system: ActorSystem) {
  import system.dispatcher
  import SessionManager._

  val saveImpl = ApiEndpoints.saveEndpoint.secure
    .serverLogicSuccess(maybeUser => new RestApiServer(dispatchActor, maybeUser).save(_))

  val forkImpl = ApiEndpoints.forkEndpoint.secure
    .serverLogic(maybeUser =>
      inputs => {
        val (clientIP, input) = inputs
        new RestApiServer(dispatchActor, maybeUser, clientIP).fork(input).map(_.toRight("Failure"))
      }
    )

  val deleteImpl = ApiEndpoints.deleteEndpoint.secure
    .serverLogicSuccess(user => new RestApiServer(dispatchActor, Some(user)).delete(_))

  val updateImpl = ApiEndpoints.updateEndpoint.secure
    .serverLogic(user =>
      inputs => {
        val (clientIP, editInputs) = inputs
        new RestApiServer(dispatchActor, Some(user), clientIP).update(editInputs).map(_.toRight("Failure"))
      }
    )

  val userSettingsImpl = ApiEndpoints.userSettingsEndpoint.secure
    .serverLogic(user => _ => Future(user.asRight))

  val userSnippetsEndpoint = ApiEndpoints.userSnippetsEndpoint.secure
    .serverLogicSuccess(user => _ => new RestApiServer(dispatchActor, Some(user)).fetchUserSnippets())

  val serverEndpoints = List(saveImpl, forkImpl, deleteImpl, updateImpl, userSettingsImpl, userSnippetsEndpoint)
}
