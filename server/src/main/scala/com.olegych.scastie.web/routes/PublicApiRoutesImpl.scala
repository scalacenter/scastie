package scastie.server.routes

import akka.actor.{ActorRef, ActorSystem}
import scastie.server.RestApiServer
import scastie.endpoints.ApiEndpoints

class PublicApiRoutesImpl(dispatchActor: ActorRef)(implicit system: ActorSystem) {
  import system.dispatcher

  val runImpl = ApiEndpoints.runEndpoint
    .serverLogicSuccess(inputs => {
      val (clientIP, input) = inputs
      new RestApiServer(dispatchActor, None, clientIP).run(input)
    })

  val formatImpl =
    ApiEndpoints.formatEndpoint
    .serverLogicSuccess(
      new RestApiServer(dispatchActor, None).format(_)
    )

  val snippetEndpoints = ApiEndpoints.snippetApiEndpoints.map { endpoint =>
    endpoint.serverLogicOption(new RestApiServer(dispatchActor, None).fetch(_))
  }

  val serverEndpoints = List(runImpl, formatImpl) ::: snippetEndpoints
}
