package scastie.endpoints


import sttp.tapir._
import sttp.tapir.json.play._
import sttp.tapir.generic.auto._
import sttp.tapir.server.akkahttp.serverSentEventsBody
import com.olegych.scastie.api._
import sttp.capabilities.akka.AkkaStreams
import OAuthEndpoints._


object StatusEndpoints {
  private val baseEndpoint = endpoint.in("api")
    .securityIn(auth.apiKey(cookie[Option[JwtCookie]](sessionCookieName)))
    .errorOut(stringBody)

  val statusSSE = baseEndpoint.in("status-sse").get.out(serverSentEventsBody)
  val statusWS = baseEndpoint.in("status-ws").out(
    webSocketBody[String, CodecFormat.TextPlain, StatusProgress, CodecFormat.Json](AkkaStreams))

  val endpoints = statusSSE :: statusWS :: Nil
}
