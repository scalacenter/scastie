package com.olegych.scastie.web

import com.olegych.scastie.web.routes._
import com.olegych.scastie.web.oauth2._
import com.olegych.scastie.balancer._
import com.olegych.scastie.util.{ShowConfig, ScastieFileUtil}
import com.typesafe.sslconfig.util.{ConfigLoader, EnrichedConfig}
import com.olegych.scastie.util.ConfigLoaders._
import akka.http.scaladsl._
import server.Directives._

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, Scheduler}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object ServerMain {
  def main(args: Array[String]): Unit = {
    val logger = Logger("ServerMain")

    val config = EnrichedConfig(
      ConfigFactory.load().getConfig("com.olegych.scastie")
    )
    val webConf = config.get[WebConf]("web")
    val balancerConf = config.get[BalancerConf]("balancer")

    val system = ActorSystem[Nothing](
      Guardian(webConf, balancerConf),
      config.get[String]("system-name")
    )

    logger.info(ShowConfig(system.settings.config,
      s"""|# Scastie sever started with config:
          |akka.remote.artery {
          |  canonical
          |  bind
          |}
          |com.olegych.scastie.web.bind
          |""".stripMargin))

    Await.result(system.whenTerminated, Duration.Inf)
  }
}

private object Guardian {
  def apply(webCfg: WebConf, balancerCfg: BalancerConf): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>
      import context.spawn
      implicit def system: ActorSystem[Nothing] = context.system
      implicit def ec: ExecutionContext = context.system.executionContext
      implicit def sc: Scheduler = context.system.scheduler

      if (webCfg.production) {
        ScastieFileUtil.writeRunningPid()
      }

      val github = new Github(webCfg.oauth2)
      val session = new GithubUserSession(
        webCfg,
        spawn(ActorRefreshTokenStorageImpl(), "refresh-token-storage")
      )
      val userDirectives = new UserDirectives(session)

      val progressActor = spawn(ProgressActor(), "ProgressActor")

      val statusActor = spawn(StatusActor(), "StatusActor")

      val dispatchActor = spawn(DispatchActor(progressActor, statusActor, balancerCfg), "DispatchActor")

      val routes = concat(
        cors()(
          pathPrefix("api")(
            concat(
              new ApiRoutes(dispatchActor, userDirectives).routes,
              new ProgressRoutes(progressActor).routes,
              new DownloadRoutes(dispatchActor).routes,
              new StatusRoutes(statusActor, userDirectives).routes,
              new ScalaJsRoutes(dispatchActor).routes
            )
          )
        ),
        new OAuth2Routes(github, session).routes,
        cors()(
          concat(
            new ScalaLangRoutes(dispatchActor, userDirectives).routes,
            new FrontPageRoutes(dispatchActor, webCfg.production).routes
          )
        )
      )

      Await.result(
        Http()
          .newServerAt(webCfg.bind.hostname, webCfg.bind.port)
          .bindFlow(routes), 1.seconds)

      Behaviors.empty
    }
}

case class WebConf(
  production: Boolean,
  oauth2: Oauth2Conf,
  sessionSecret: String,
  bind: BindConf,
)
object WebConf {
  implicit val loader: ConfigLoader[WebConf] = (c: EnrichedConfig) => WebConf(
    c.get[Boolean]("production"),
    c.get[Oauth2Conf]("oauth2"),
    c.get[String]("session-secret"),
    c.get[BindConf]("bind")
  )
}
case class BindConf(
  hostname: String,
  port: Int,
)
object BindConf {
  implicit val loader: ConfigLoader[BindConf] = (c: EnrichedConfig) => BindConf(
    c.get[String]("hostname"),
    c.get[Int]("port")
  )
}
