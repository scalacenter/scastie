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

case class ScastiePresentationCompiler(pc: PresentationCompiler) {
  def complete[F[_]: Async](offsetParams: ScastieOffsetParams): F[CompletionList] =
    Async[F].fromFuture(Async[F].delay(pc.complete(offsetParams.toOffsetParams).asScala))

  def completionItemResolve[F[_]: Async](completionItem: CompletionItemDTO): F[String] =
    Async[F].executionContext.flatMap { implicit ec =>
      completionItem.symbol
        .map { symbol =>
          Async[F].fromFuture(Async[F].delay {
            val completionItemJ = CompletionItem(completionItem.label)
            completionItemJ.setDetail(completionItem.detail)
            pc.completionItemResolve(completionItemJ, symbol)
              .asScala
              .map(_.getDocstring)
          })
        }
        .getOrElse("".pure)
    }

  def hover[F[_]: Async](offsetParams: ScastieOffsetParams): F[Either[FailureType, Hover]] =
    (Async[F].executionContext >>= { implicit ec =>
      Async[F].fromFuture(Async[F].delay {
        pc.hover(offsetParams.toOffsetParams)
          .asScala
          .map(_.toScala.toRight(NoResult("There is no hover for given position")))
      })
    })

  def signatureHelp[F[_]: Async](offsetParams: ScastieOffsetParams): F[SignatureHelp] =
    Async[F].fromFuture(Async[F].delay(pc.signatureHelp(offsetParams.toOffsetParams).asScala))

}
