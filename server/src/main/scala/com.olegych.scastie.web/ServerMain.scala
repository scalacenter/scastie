package scastie.server

import akka.actor.ActorSystem
import akka.actor.Props
import akka.http.scaladsl._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.olegych.scastie.balancer._
import com.olegych.scastie.util.ScastieFileUtil
import scastie.server.routes._
import com.typesafe.scalalogging.Logger

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import server.Directives._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.server.akkahttp.AkkaHttpServerOptions
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher
import akka.http.scaladsl.model.HttpMethods

object ServerMain {
  val logger = Logger("Server")

  def main(args: Array[String]): Unit = {

    if (ServerConfig.production) {
      ScastieFileUtil.writeRunningPid("RUNNING_PID")
    }

    implicit val system: ActorSystem = ActorSystem("Web")
    import system.dispatcher

    val progressActor = system.actorOf(Props[ProgressActor](), name = "ProgressActor")
    val statusActor = system.actorOf(StatusActor.props, name = "StatusActor")
    val dispatchActor = system.actorOf(Props(new DispatchActor(progressActor, statusActor)), name = "DispatchActor")

    val apiServerEndpoints = new ApiRoutesImpl(dispatchActor).serverEndpoints
    val publicApiServerEndpoints = new PublicApiRoutesImpl(dispatchActor).serverEndpoints
    val progressServerEndpoints = new ProgressRoutesImpl(progressActor).serverEndpoints
    val downloadServerEndpoints = new DownloadRoutesImpl(dispatchActor).serverEndpoints
    val statusServerEndpoints = new StatusRoutesImpl(statusActor).serverEndpoints
    val oauthServerEndpoints = new OAuthRoutesImpl().serverEndpoints
    val frontPageServerEndpoints = new FrontPageEndpointsImpl(dispatchActor).serverEndpoints
    val docsServerEndpoints =  new DocsRoutes().serverEndpoints

    def serverLogger = AkkaHttpServerOptions.defaultSlf4jServerLog.copy(
      logWhenReceived = true,
      logWhenHandled = true,
      logAllDecodeFailures = true,
    )

    val defaultSettings = AkkaHttpServerOptions
      .customiseInterceptors
      .serverLog(serverLogger)
      .options

    val hostOriginRoutes = AkkaHttpServerInterpreter(defaultSettings).toRoute(
      List(
        oauthServerEndpoints,
        docsServerEndpoints,
        statusServerEndpoints,
        downloadServerEndpoints,
        apiServerEndpoints
      ).flatten
    )

    val crossOriginRoutes = AkkaHttpServerInterpreter(defaultSettings).toRoute(
      List(
        publicApiServerEndpoints,
        progressServerEndpoints,
        frontPageServerEndpoints,
      ).flatten
    )

    val publicCORSSettings =
      CorsSettings(system)
        .withAllowedOrigins(HttpOriginMatcher.*)
        .withAllowCredentials(false)
        .withAllowedMethods(List(HttpMethods.GET, HttpMethods.POST, HttpMethods.OPTIONS))

    val routes = concat(
      cors()(hostOriginRoutes),
      cors(publicCORSSettings)(crossOriginRoutes)
    )

    val futureBinding = Http().newServerAt("localhost", ServerConfig.port).bindFlow(routes)

    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }

    Await.result(system.whenTerminated, Duration.Inf)
  }

}
