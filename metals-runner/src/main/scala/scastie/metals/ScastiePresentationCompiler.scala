package scastie.metals

import java.nio.file.Path
import java.util.concurrent.TimeoutException
import scala.concurrent.duration._
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
import org.slf4j.LoggerFactory
import DTOExtensions._
import JavaConverters._

case class ScastiePresentationCompiler(underlyingPC: PresentationCompiler) {
  private val logger = LoggerFactory.getLogger(getClass)

  def inifiniteCompilationDetection[F[_]: Async, Input, Output](task: F[Output])(input: Input): F[Output] =
    Async[F].timeout(task, 30.seconds).onError { case _: TimeoutException =>
      Async[F].pure {
        logger.error("FATAL ERROR: Timeout while computing completions, possible inifinite compilation")
        logger.error(input.toString)
      }
    }

  def complete[F[_]: Async](offsetParams: ScastieOffsetParams): F[ScalaCompletionList] =
    val task =
      for
        given ExecutionContext <- Async[F].executionContext
        computationFuture = Async[F].delay:
          val lspOffsetParams = offsetParams.toOffsetParams
          underlyingPC
            .complete(lspOffsetParams)
            .asScala
            .map:
              _.toScalaCompletionList(offsetParams.isWorksheetMode)
        result <- Async[F].fromFuture(computationFuture)
      yield result

    inifiniteCompilationDetection(task)(offsetParams)

  def completionItemResolve[F[_]: Async](completionItem: CompletionItemDTO): F[String] =
    val task =
      for
        given ExecutionContext <- Async[F].executionContext
        computationFuture = Async[F].delay {
          completionItem.symbol
            .map { symbol =>
              val completionItemJ = CompletionItem(completionItem.label)
              completionItemJ.setDetail(completionItem.detail)
              underlyingPC
                .completionItemResolve(completionItemJ, symbol)
                .asScala
                .map(_.getDocstring)
            }
            .getOrElse(Future.successful(""))
        }
        result <- Async[F].fromFuture(computationFuture)
      yield result

    inifiniteCompilationDetection(task)(completionItem)

  def hover[F[_]: Async](offsetParams: ScastieOffsetParams): F[Either[FailureType, Hover]] =
    val task: F[Either[FailureType, Hover]] =
      for
        given ExecutionContext <- Async[F].executionContext
        computationFuture = Async[F].delay {
          underlyingPC
            .hover(offsetParams.toOffsetParams)
            .asScala
            .map(_.toScala.map(_.toLsp).toRight(NoResult("There is no hover for given position")))
        }
        result <- Async[F].fromFuture(computationFuture)
      yield result

    inifiniteCompilationDetection(task)(offsetParams)

  def signatureHelp[F[_]: Async](offsetParams: ScastieOffsetParams): F[SignatureHelp] =
    Async[F].fromFuture(Async[F].delay(underlyingPC.signatureHelp(offsetParams.toOffsetParams).asScala))

}
