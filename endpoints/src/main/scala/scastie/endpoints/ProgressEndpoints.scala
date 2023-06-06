package scastie.endpoints


import sttp.tapir._
import sttp.tapir.json.play._
import sttp.tapir.generic.auto._
import sttp.tapir.server.akkahttp.serverSentEventsBody
import com.olegych.scastie.api._
import sttp.capabilities.akka.AkkaStreams


object ProgressEndpoints {

  val endpointBase = endpoint.in("api")
  val progressSSE =
    SnippetMatcher.getApiSnippetEndpoints(endpointBase.in("progress-sse")).map { endpoint =>
      endpoint.get.out(serverSentEventsBody)
    }

  val progressWS =
    SnippetMatcher.getApiSnippetEndpoints(endpointBase.in("progress-ws")).map { endpoint =>
      endpoint.out(
        webSocketBody[String, CodecFormat.TextPlain, SnippetProgress, CodecFormat.Json](AkkaStreams))
    }

  val endpoints = progressSSE ++ progressWS
}
