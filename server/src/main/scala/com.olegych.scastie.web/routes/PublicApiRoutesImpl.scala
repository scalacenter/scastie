package com.olegych.scastie.web.routes

import akka.actor.{ActorRef, ActorSystem}
import com.olegych.scastie.web._
import scastie.endpoints.ApiEndpoints
import akka.http.scaladsl.model.RemoteAddress

class PublicApiRoutesImpl(dispatchActor: ActorRef)(implicit system: ActorSystem) {
  import system.dispatcher

  val runImpl = ApiEndpoints.runEndpoint
    .serverLogicSuccess(
      new RestApiServer(dispatchActor, RemoteAddress.Unknown, None).run(_)
    )

  val formatImpl =
    ApiEndpoints.formatEndpoint
    .serverLogicSuccess(
      new RestApiServer(dispatchActor, RemoteAddress.Unknown, None).format(_)
    )

  val snippetEndpoints = ApiEndpoints.snippetApiEndpoints.map { endpoint =>
    endpoint.serverLogicOption(new RestApiServer(dispatchActor, RemoteAddress.Unknown, None).fetch(_))
  }

  val serverEndpoints = List(runImpl, formatImpl) ::: snippetEndpoints
}
