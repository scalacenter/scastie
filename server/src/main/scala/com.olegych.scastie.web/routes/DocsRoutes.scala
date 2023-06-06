package com.olegych.scastie.web.routes

import scastie.endpoints.ApiEndpoints
import scala.concurrent.Future
import scastie.endpoints.OAuthEndpoints
import scastie.endpoints._
import sttp.tapir.redoc.bundle.RedocInterpreter

class DocsRoutes() {
  private val allEndpoints =
    OAuthEndpoints.endpoints ++
    FrontPageEndpoints.endpoints ++
    ApiEndpoints.endpoints ++
    ProgressEndpoints.endpoints ++
    DownloadEndpoints.endpoints ++
    StatusEndpoints.endpoints

  val serverEndpoints = RedocInterpreter().fromEndpoints[Future](allEndpoints, "Scastie", "1.0.0-RC1")
}
