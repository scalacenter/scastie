package scastie.metals

import java.nio.file.Path
import scala.concurrent.ExecutionContext
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

case class ScastiePresentationCompiler(pc: PresentationCompiler, metalsWorkingDirectory: Path) {
  def complete[F[_]: Async](offsetParams: ScastieOffsetParams): F[CompletionList] =
    Async[F].fromFuture(pc.complete(offsetParams.toOffsetParams).asScala.pure)

  def completionItemResolve[F[_]: Async](completionItem: CompletionItemDTO)(
    implicit ec: ExecutionContext
  ): F[String] = completionItem.symbol
    .map { symbol =>
      val completionItemJ = CompletionItem(completionItem.label)
      completionItemJ.setDetail(completionItem.detail)
      val doc = pc
        .completionItemResolve(completionItemJ, symbol)
        .asScala
        .map(_.getDocstring)
      Async[F].fromFuture(doc.pure)
    }
    .getOrElse(Async[F].fromFuture(Future("").pure))

  def hover[F[_]: Async](offsetParams: ScastieOffsetParams)(
    implicit ec: ExecutionContext
  ): EitherT[F, FailureType, Hover] =
    val javaHover = pc
      .hover(offsetParams.toOffsetParams)
      .asScala
      .map(_.toScala.toRight(NoResult("There is no hover for given position")))
    EitherT(Async[F].fromFuture(javaHover.pure))

  def signatureHelp[F[_]: Async](offsetParams: ScastieOffsetParams): F[SignatureHelp] =
    Async[F].fromFuture(pc.signatureHelp(offsetParams.toOffsetParams).asScala.pure)

}
