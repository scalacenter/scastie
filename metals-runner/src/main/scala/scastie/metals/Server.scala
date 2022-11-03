package scastie.metals

import cats.effect.{Async, Resource}
import cats.syntax.all._
import fs2.Stream
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.middleware._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import com.comcast.ip4s._
import java.lang.management.ManagementFactory
import java.nio.file.{Paths, Files}
import java.nio.charset.StandardCharsets

object Server:
  def writeRunningPid(name: String): String = {
    val pid = ManagementFactory.getRuntimeMXBean.getName.split("@").head
    val pidFile = Paths.get(name)
    Files.write(pidFile, pid.getBytes(StandardCharsets.UTF_8))
    sys.addShutdownHook {
      Files.delete(pidFile)
    }
    pid
  }

  def stream[F[_]: Async]: Stream[F, Nothing] = {
    for {
      client <- Stream.resource(EmberClientBuilder.default[F].build)
      metalsImpl = ScastieMetalsImpl.instance[F]

      httpApp = ScastieMetalsRoutes.routes[F](metalsImpl).orNotFound

      corsService = CORS.policy.withAllowOriginAll(httpApp)
      finalHttpApp = Logger.httpApp(true, false)(corsService)
      _ = if (true) writeRunningPid("METALS_RUNNING_PID")

      exitCode <- Stream.resource(
        EmberServerBuilder.default[F]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"8000")
          .withHttpApp(finalHttpApp)
          .build >>
        Resource.eval(Async[F].never)
      )
    } yield exitCode
  }.drain

