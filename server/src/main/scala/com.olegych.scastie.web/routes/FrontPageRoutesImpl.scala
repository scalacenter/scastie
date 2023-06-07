package scastie.server.routes

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl.StreamConverters
import akka.util.Timeout
import com.olegych.scastie.api.FetchResult
import com.olegych.scastie.api.SnippetId
import com.olegych.scastie.api.SnippetUserPart
import com.olegych.scastie.balancer.FetchSnippet
import com.olegych.scastie.util.Base64UUID
import org.apache.commons.text.StringEscapeUtils

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scastie.endpoints.FrontPageEndpoints
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.files.FilesOptions
import sttp.tapir.files.Resources
import scastie.endpoints.FrontPageEndpoints.ColorScheme
import scastie.endpoints.SnippetIdUtils._
import scastie.server.ServerConfig


class FrontPageEndpointsImpl(dispatchActor: ActorRef)(implicit ec: ExecutionContext, mat: Materializer) {
  implicit val timeout: Timeout = Timeout(20.seconds)

  val fileOptions: FilesOptions[Future] = FilesOptions
    .default
    .withUseGzippedIfAvailable
    .defaultFile(List("index.html"))

  val embeddedJSEndpointImpl = ServerEndpoint.public(
      FrontPageEndpoints.embeddedJSEndpoint,
      Resources.get(this.getClass.getClassLoader(), "public/embedded/embedded.js", fileOptions)(_)
    )

  val embeddedCSSEndpointImpl = ServerEndpoint.public(
      FrontPageEndpoints.embeddedCSSEndpoint,
      Resources.get(this.getClass.getClassLoader(), "public/embedded/style.css", fileOptions)(_)
    )

  val publicAssetsEndpointImpl = ServerEndpoint.public(
      FrontPageEndpoints.publicAssetsEndpoint,
      Resources.get(this.getClass.getClassLoader(), "public/", fileOptions)(_)
    )

  val indexEndpointImpl = ServerEndpoint.public(
      FrontPageEndpoints.indexEndpoint,
      Resources.get(this.getClass.getClassLoader(), "public/index.html", fileOptions)(_)
    )

  val frontPageSnippetEndpointsImpl = FrontPageEndpoints.frontPageSnippetEndpoints.map { endpoint =>
    endpoint.serverLogicSuccess[Future] {
      case (Right(snippetId: EmbeddedSnippetId), theme) => getEmbeddedSnippet(snippetId, theme)
      case (Left(snippetId: NormalSnippetId), _) => getNormalSnippet(snippetId)
    }
  }

  private val placeholders = List(
    "Scastie can run any Scala program with any library in your browser. You don’t need to download or install anything.",
  )

  private val indexResource = "public/index.html"
  private val indexResourceContent = Future.traverse(Option(getClass.getClassLoader.getResource(indexResource)).toList) { url =>
    StreamConverters.fromInputStream(() => url.openStream()).runFold("")(_ + _.utf8String)
  }

  private def getNormalSnippet(snippetId: SnippetId): Future[UniversalSnippet] =
    for {
      snippet <- dispatchActor.ask(FetchSnippet(snippetId)).mapTo[Option[FetchResult]]
      content <- indexResourceContent
    } yield
      content.headOption.map { indexContent =>
        val code = StringEscapeUtils.escapeHtml4(snippet.fold("Snippet not found")(_.inputs.code))
        val replacedContent = placeholders.foldLeft(indexContent)(_.replace(_, code))
        UniversalSnippet(replacedContent)
      }.getOrElse(UniversalSnippet("Resource not found"))

  private def getEmbeddedSnippet(snippetId: SnippetId, theme: Option[ColorScheme.Value]): Future[EmbeddedSnippet] = Future {
    val user = snippetId.user match {
      case Some(SnippetUserPart(login, update)) =>
        s"user: '$login', update: $update,"
      case None => ""
    }

    val themePart = theme match {
      case Some(t) => s"theme: '$t',"
      case None    => ""
    }

    val id = "id-" + Base64UUID.create

    val embeddedUrlBase =
      if (ServerConfig.production) s"https://${ServerConfig.hostname}"
      else s"http://localhost:${ServerConfig.port}"

    val result = s"""|document.write("
                     |<div id='$id'></div>
                     |<script src='$embeddedUrlBase/embedded.js'></script>
                     |<script>
                     |window.addEventListener('load', function(event) {
                     |  scastie.EmbeddedResource({
                     |    $themePart
                     |    base64UUID: '${snippetId.base64UUID}',
                     |    $user
                     |    injectId: '$id',
                     |    serverUrl: '$embeddedUrlBase'
                     |  });
                     |});
                     |</script>
                     |");""".stripMargin.split("\n").map(_.trim).mkString("")

    EmbeddedSnippet(result)
  }


  val serverEndpoints = List(embeddedJSEndpointImpl, embeddedCSSEndpointImpl, publicAssetsEndpointImpl, indexEndpointImpl) ++ frontPageSnippetEndpointsImpl
}
