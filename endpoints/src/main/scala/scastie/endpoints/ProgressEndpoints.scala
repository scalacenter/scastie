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
    SnippetMatcher
      .getApiSnippetEndpoints(endpointBase.in("progress-sse"), "SSE progress for")
      .map { endpoint =>
        endpoint.out(serverSentEventsBody)
        .description(
          """|Endpoint used to connect to EventStream for specific snippet Id.
             |The connection to it should be instantly estabilished after the snippet is run.
             |
             |Received events are of type `SnippetProgress`, and contain all output of both
             |the compilation and runtime of the snippet.
             |
             |Output is split into `ConsoleOutput` type hierarchy:
             | - `SbtOutput` - output from sbt shell
             | - `UserOutput` - runtime output
             | - `ScastieOutput` - output from scastie server
             |
             |""".stripMargin
          )
      }


  val progressWS =
    SnippetMatcher
      .getApiSnippetEndpoints(endpointBase.in("progress-ws"), "Websocket progress for")
      .map { endpoint =>
        endpoint.out(
          webSocketBody[String, CodecFormat.TextPlain, SnippetProgress, CodecFormat.Json](AkkaStreams))
      }

  val endpoints = progressSSE ++ progressWS
}
