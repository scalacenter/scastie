package scastie.metals

import cats.effect.Async
import cats.syntax.all._
import com.olegych.scastie.api._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server._

object ScastieMetalsRoutes {

  def routes[F[_]: Async](metals: ScastieMetals[F]): HttpRoutes[F] =
    val dsl = new Http4sDsl[F] {}
    import dsl._
    import DTOCodecs._
    import JavaConverters._

    implicit val lspRequestDecoder: EntityDecoder[F, LSPRequestDTO]                  = jsonOf[F, LSPRequestDTO]
    implicit val scastieMetalsOptionsDecoder: EntityDecoder[F, ScastieMetalsOptions] = jsonOf[F, ScastieMetalsOptions]
    implicit val completionInfoDecoder: EntityDecoder[F, CompletionInfoRequest]      = jsonOf[F, CompletionInfoRequest]

    HttpRoutes.of[F] {
      case req @ POST -> Root / "metals" / "complete" => for {
          lspRequest       <- req.as[LSPRequestDTO]
          maybeCompletions <- metals.complete(lspRequest).value
          resp             <- Ok(maybeCompletions.asJson)
        } yield resp

      case req @ POST -> Root / "metals" / "completionItemResolve" => for {
          completionInfoRequest <- req.as[CompletionInfoRequest]
          maybeCompletionInfo   <- metals.completionInfo(completionInfoRequest).value
          resp                  <- Ok(maybeCompletionInfo.asJson)
        } yield resp

      case req @ POST -> Root / "metals" / "hover" => for {
          lspRequest <- req.as[LSPRequestDTO]
          hover      <- metals.hover(lspRequest).value
          resp       <- Ok(hover.map(_.toHoverDTO).asJson)
        } yield resp

      case req @ POST -> Root / "metals" / "signatureHelp" => for {
          lspRequest    <- req.as[LSPRequestDTO]
          signatureHelp <- metals.signatureHelp(lspRequest).value
          resp          <- Status.NotImplemented()
        } yield resp

      case req @ POST -> Root / "metals" / "isConfigurationSupported" => for {
          scastieConfiguration     <- req.as[ScastieMetalsOptions]
          isConfigurationSupported <- metals.isConfigurationSupported(scastieConfiguration).value
          resp                     <- Ok(isConfigurationSupported.asJson)
        } yield resp

    }

}
