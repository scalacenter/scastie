package com.olegych.scastie.web.routes

import com.olegych.scastie.web.ServerConfig
import scastie.endpoints.ApiEndpoints
import scastie.endpoints._
import sttp.tapir.redoc.bundle.RedocInterpreter

import scala.concurrent.Future

class DocsRoutes() {
  private val publicEndpoints =
    FrontPageEndpoints.endpoints ++
    ApiEndpoints.publicEndpoints ++
    ProgressEndpoints.endpoints

  private val allEndpoints = publicEndpoints ++
    DownloadEndpoints.endpoints ++
    StatusEndpoints.endpoints ++
    OAuthEndpoints.endpoints

  val endpointsForDocumentation = if (ServerConfig.production)
    publicEndpoints
  else
    allEndpoints

  val serverEndpoints = RedocInterpreter()
    .fromEndpoints[Future](endpointsForDocumentation, "Scastie", "1.0.0-RC1")
}
