package scastie.metals

import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import cats.effect.{Async, Resource}
import cats.syntax.all._
import com.comcast.ip4s._
import com.typesafe.config.ConfigFactory
import fs2.Stream
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware._

object Server:

  def writeRunningPid(name: String): String = {
    val pid     = ManagementFactory.getRuntimeMXBean.getName.split("@").head
    val pidFile = Paths.get(name)
    Files.write(pidFile, pid.getBytes(StandardCharsets.UTF_8))
    sys.addShutdownHook {
      Files.delete(pidFile)
    }
    pid
  }

  val config                   = ConfigFactory.load().getConfig("scastie.metals")
  val cacheExpirationInSeconds = config.getInt("cache-expire-in-seconds")
  val isProduction             = config.getBoolean("production")

  def stream[F[_]: Async]: Stream[F, Nothing] = {
    for {
      client <- Stream.resource(EmberClientBuilder.default[F].build)

      metalsImpl = ScastieMetalsImpl.instance[F](cacheExpirationInSeconds)

      httpApp = ScastieMetalsRoutes.routes[F](metalsImpl).orNotFound

      corsService  = CORS.policy.withAllowOriginAll(httpApp)
      finalHttpApp = Logger.httpApp(true, false)(corsService)
      _            = if (isProduction) writeRunningPid("METALS_RUNNING_PID")

      exitCode <- Stream.resource(
        EmberServerBuilder
          .default[F]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"8000")
          .withHttpApp(finalHttpApp)
          .build >>
          Resource.eval(Async[F].never)
      )
    } yield exitCode
  }.drain
