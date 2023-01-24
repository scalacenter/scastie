package scastie.metals

import java.nio.file.Path
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._
import scala.meta.pc.PresentationCompiler

import cats.data.EitherT
import cats.effect.Async
import cats.syntax.all._
import com.olegych.scastie.api._
import org.eclipse.lsp4j._
import DTOExtensions._
import JavaConverters._

case class ScastiePresentationCompiler(underlyingPC: PresentationCompiler) {
  def complete[F[_]: Async](offsetParams: ScastieOffsetParams): F[CompletionList] =
    Async[F].fromFuture(Async[F].delay(underlyingPC.complete(offsetParams.toOffsetParams).asScala))

  def completionItemResolve[F[_]: Async](completionItem: CompletionItemDTO): F[String] =
    Async[F].executionContext.flatMap { implicit ec =>
      completionItem.symbol
        .map { symbol =>
          Async[F].fromFuture(Async[F].delay {
            val completionItemJ = CompletionItem(completionItem.label)
            completionItemJ.setDetail(completionItem.detail)
            underlyingPC.completionItemResolve(completionItemJ, symbol)
              .asScala
              .map(_.getDocstring)
          })
        }
        .getOrElse("".pure)
    }

  def hover[F[_]: Async](offsetParams: ScastieOffsetParams): F[Either[FailureType, Hover]] =
    (Async[F].executionContext >>= { implicit ec =>
      Async[F].fromFuture(Async[F].delay {
        underlyingPC.hover(offsetParams.toOffsetParams)
          .asScala
          .map(_.toScala.map(_.toLsp).toRight(NoResult("There is no hover for given position")))
      })
    })

  def signatureHelp[F[_]: Async](offsetParams: ScastieOffsetParams): F[SignatureHelp] =
    Async[F].fromFuture(Async[F].delay(underlyingPC.signatureHelp(offsetParams.toOffsetParams).asScala))

}
