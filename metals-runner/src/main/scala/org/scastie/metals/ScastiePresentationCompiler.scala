package org.scastie.metals

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
import org.scastie.api._
import org.eclipse.lsp4j._
import org.slf4j.LoggerFactory
import DTOExtensions._
import JavaConverters._
import cats.effect.IO
import scala.meta.pc.RawPresentationCompiler
import scala.meta.internal.metals.CompilerVirtualFileParams
import scala.meta.pc.VirtualFileParams
import java.net.URI
import scala.meta.pc.CancelToken
import scala.meta.internal.metals.EmptyCancelToken
import scala.reflect.internal.util.NoSourceFile
import cats.effect.std.Semaphore
import ch.epfl.scala.debugadapter.internal.scalasig.Ref

sealed trait ScastiePresentationCompiler {
  def complete(offsetParams: ScastieOffsetParams): IO[ScalaCompletionList]

  def completionItemResolve(completionItem: CompletionItemDTO): IO[String]

  def hover(offsetParams: ScastieOffsetParams): EitherT[IO, FailureType, Hover]

  def signatureHelp(offsetParams: ScastieOffsetParams): IO[SignatureHelp]

  def diagnostics(scastieOptions: ScastieOffsetParams): IO[Set[Problem]]

  def shutdown(): IO[Unit]
}

object ScastiePresentationCompiler {
  def apply(underlyingPC: PresentationCompiler | RawPresentationCompiler): IO[ScastiePresentationCompiler] =
    underlyingPC match
      case pc: PresentationCompiler    =>
        IO.pure(OldScastiePresentationCompiler(pc))
      case pc: RawPresentationCompiler =>
        Semaphore[IO](1).map(RawScastiePresentationCompiler(pc, _))
}

case class RawScastiePresentationCompiler(underlyingPC: RawPresentationCompiler, semaphore: Semaphore[IO]) extends ScastiePresentationCompiler {

  def complete(offsetParams: ScastieOffsetParams): IO[ScalaCompletionList] =
    semaphore.permit.use { _ =>
      IO.interruptible {
        val (lspOffsetParams, insideWrapper) = offsetParams.toOffsetParams
        underlyingPC.complete(lspOffsetParams, CompletionTriggerKind.Invoked)
          .toScalaCompletionList(offsetParams.isWorksheetMode, insideWrapper)
      }
    }

  def completionItemResolve(completionItem: CompletionItemDTO): IO[String] =
    semaphore.permit.use { _ =>
      IO.interruptible {
        val completionItemJ = CompletionItem(completionItem.label)
        completionItemJ.setDetail(completionItem.detail)
        completionItem.symbol.map { symbol =>
          underlyingPC
            .completionItemResolve(completionItemJ, symbol)
            .getDocstring
        }.getOrElse("")
      }
    }

  def hover(offsetParams: ScastieOffsetParams): EitherT[IO, FailureType, Hover] =
    EitherT {
      semaphore.permit.use { _ =>
        IO.interruptible {
          underlyingPC
            .hover(offsetParams.toOffsetParams._1)
            .toScala
            .map(_.toLsp)
            .toRight(NoResult("There is no hover for given position"))
        }
      }
    }

  def signatureHelp(offsetParams: ScastieOffsetParams): IO[SignatureHelp] =
    semaphore.permit.use { _ =>
      IO.interruptible {
        underlyingPC.signatureHelp(offsetParams.toOffsetParams._1)
      }
    }

  def diagnostics(scastieOptions: ScastieOffsetParams): IO[Set[Problem]] =
    semaphore.permit.use { _ =>
      IO.interruptible {
        underlyingPC.didChange(scastieOptions.toDiagnosticParams).asScala.toSet.map { diag =>
          diag.toProblem(scastieOptions.isWorksheetMode)
        }
      }
    }

  def shutdown(): IO[Unit] = IO.unit
}


case class OldScastiePresentationCompiler(underlyingPC: PresentationCompiler) extends ScastiePresentationCompiler {
  private val logger = LoggerFactory.getLogger(getClass)

  def inifiniteCompilationDetection[Input, Output](task: IO[Output])(input: Input): IO[Output] =
    task.timeout(30.seconds).onError { case _: TimeoutException =>
      IO.pure {
        logger.error("FATAL ERROR: Timeout while computing completions, possible inifinite compilation")
        logger.error(input.toString)
      }
    }

  def complete(offsetParams: ScastieOffsetParams): IO[ScalaCompletionList] =
    val task =
      for
        given ExecutionContext <- IO.executionContext
        computationFuture = IO {
          val (lspOffsetParams, insideWrapper) = offsetParams.toOffsetParams
          underlyingPC
            .complete(lspOffsetParams)
            .asScala
            .map:
              _.toScalaCompletionList(offsetParams.isWorksheetMode, insideWrapper)
        }
        result <- IO.fromFuture(computationFuture)
      yield result

    inifiniteCompilationDetection(task)(offsetParams)

  def completionItemResolve(completionItem: CompletionItemDTO): IO[String] =
    val task =
      for
        given ExecutionContext <- IO.executionContext
        computationFuture = IO {
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
        result <- IO.fromFuture(computationFuture)
      yield result

    inifiniteCompilationDetection(task)(completionItem)

  def hover(offsetParams: ScastieOffsetParams): EitherT[IO, FailureType, Hover] =
    val task: IO[Either[FailureType, Hover]] =
      for
        given ExecutionContext <- IO.executionContext
        computationFuture = IO {
          underlyingPC
            .hover(offsetParams.toOffsetParams._1)
            .asScala
            .map(_.toScala.map(_.toLsp).toRight(NoResult("There is no hover for given position")))
        }
        result <- IO.fromFuture(computationFuture)
      yield result

    EitherT(inifiniteCompilationDetection(task)(offsetParams))

  def signatureHelp(offsetParams: ScastieOffsetParams): IO[SignatureHelp] =
    IO.fromFuture(IO(underlyingPC.signatureHelp(offsetParams.toOffsetParams._1).asScala))

  def diagnostics(scastieOptions: ScastieOffsetParams): IO[Set[Problem]] = IO.pure(Set.empty)

  def shutdown(): IO[Unit] = IO(underlyingPC.shutdown())

}
