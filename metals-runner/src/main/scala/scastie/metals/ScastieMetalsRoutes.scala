package scastie.metals


import cats.effect._
import cats.syntax.all._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

import io.circe.syntax._
import io.circe._
import org.http4s.circe._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.ember.server._
import org.http4s.jawn._
import org.http4s.implicits._
import com.google.gson.Gson
import com.olegych.scastie.api._
import scala.jdk.CollectionConverters._


object ScastieMetalsRoutes {
  def routes[F[_]: Async](metals: ScastieMetals[F]): HttpRoutes[F] =
    val dsl = new Http4sDsl[F]{}
    import dsl._
    import DTOCodecs._
    import JavaConverters._

    val gson = new Gson()

    implicit val lspRequestdecoder: EntityDecoder[F, LSPRequestDTO] = jsonOf[F, LSPRequestDTO]
    implicit val completionInfoDecoder: EntityDecoder[F, CompletionInfoRequest] = jsonOf[F, CompletionInfoRequest]

    HttpRoutes.of[F] {
      case req @ POST -> Root / "metals" / "complete" =>
        for {
          lspRequest <- req.as[LSPRequestDTO]
          completions <- metals.complete(lspRequest).value
          resp <- completions match {
            case Left(error) => BadRequest(error)
            case Right(completionList) => Ok(completionList.toSimpleScalaList.asJson)
          }
        } yield resp

      case req @ POST -> Root / "metals" / "completionInfo" =>
        for {
          completionInfoRequest <- req.as[CompletionInfoRequest]
          completions <- metals.completionInfo(completionInfoRequest).value
          resp <- completions match {
            case Left(error) => BadRequest(error)
            case Right(doc) => Ok(doc)
          }
        } yield resp

      case req @ POST -> Root / "metals" / "hover" =>
        for {
          lspRequest <- req.as[LSPRequestDTO]
          hover <- metals.hover(lspRequest).value
          resp <- hover match {
            case Left(error) => BadRequest(error)
            case Right(hover) => Ok(gson.toJson(hover))
          }
        } yield resp

      case req @ POST -> Root / "metals" / "signatureHelp" =>
        for {
          lspRequest <- req.as[LSPRequestDTO]
          signatureHelp <- metals.signatureHelp(lspRequest).value
          resp <- signatureHelp match {
            case Left(error) => BadRequest(error)
            case Right(signatureHelp) => Ok(gson.toJson(signatureHelp))
          }
        } yield resp

  }
}
