package scastie.metals

import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.concurrent.duration.*

import cats.effect.{Async, Resource}
import cats.effect.implicits.*
import cats.syntax.all._
import com.comcast.ip4s._
import com.evolutiongaming.scache.{Cache, ExpiringCache}
import com.olegych.scastie.api.ScastieMetalsOptions
import com.typesafe.config.ConfigFactory
import fs2.Stream
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware._

object Server:

  val config                   = ConfigFactory.load().getConfig("scastie.metals")
  val cacheExpirationInSeconds = config.getInt("cache-expire-in-seconds")
  val serverPort               = config.getInt("port")

  def stream[F[_]: Async]: Stream[F, Nothing] = {
    val cache = Cache.expiring[F, ScastieMetalsOptions, ScastiePresentationCompiler](
      ExpiringCache.Config(expireAfterRead = cacheExpirationInSeconds.seconds, maxSize = Some(64)),
      None
    )

    val finalHttpApp = (cache0: Cache[F, ScastieMetalsOptions, ScastiePresentationCompiler]) => {
      val metalsImpl  = ScastieMetalsImpl.instance[F](cache0)
      val httpApp     = ScastieMetalsRoutes.routes[F](metalsImpl).orNotFound
      val corsService = CORS.policy.withAllowOriginAll(httpApp)
      Logger.httpApp(true, false)(corsService)
    }

    Stream.resource(
      cache.flatMap(cache =>
        EmberServerBuilder
          .default[F]
          .withHost(ipv4"0.0.0.0")
          .withPort(Port.fromInt(serverPort).getOrElse(port"8000"))
          .withHttpApp(finalHttpApp(cache))
          .build >>
          Resource.eval(Async[F].never)
      )
    )
  }.drain
