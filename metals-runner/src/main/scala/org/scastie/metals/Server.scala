package org.scastie.metals

import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.concurrent.duration.*

import cats.effect.{Async, Resource}
import cats.effect.implicits.*
import cats.syntax.all._
import com.comcast.ip4s._
import com.evolutiongaming.scache.{Cache, ExpiringCache}
import org.scastie.api.ScastieMetalsOptions
import com.typesafe.config.ConfigFactory
import fs2.Stream
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware._
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.ExitCode

object Server extends IOApp:

  val config                   = ConfigFactory.load().getConfig("scastie.metals")
  val cacheExpirationInSeconds = config.getInt("cache-expire-in-seconds")
  val serverPort               = config.getInt("port")

  val cache = Cache.expiring[IO, (String, ScastieMetalsOptions), ScastiePresentationCompiler](
    ExpiringCache.Config(expireAfterRead = cacheExpirationInSeconds.seconds, maxSize = Some(64)),
    None
  )

  override def run(args: List[String]): IO[ExitCode] =

    val app = for {
      cache <- cache
      impl = ScastieMetalsImpl.instance(cache)
      app = ScastieMetalsRoutes.routes(impl).orNotFound
      corsApp = CORS.policy.withAllowOriginAll(app)
      loggingApp = Logger.httpApp(true, false)(corsApp)
      _ <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(Port.fromInt(serverPort).getOrElse(port"8000"))
        .withHttpApp(loggingApp)
        .build
    } yield ()

    app.useForever.as(ExitCode.Success)


