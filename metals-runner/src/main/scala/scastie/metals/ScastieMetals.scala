package scastie.metals

import cats.syntax.all._
import org.eclipse.lsp4j._
import cats.effect.Async
import cats.data.EitherT
import scala.concurrent.ExecutionContext.Implicits._
import com.olegych.scastie.api._

trait ScastieMetals[F[_]]:
  def complete(request: LSPRequestDTO): EitherT[F, FailureType, CompletionList]
  def completionInfo(request: CompletionInfoRequest): EitherT[F, FailureType, String]
  def hover(request: LSPRequestDTO): EitherT[F, FailureType, Hover]
  def signatureHelp(request: LSPRequestDTO): EitherT[F, FailureType, SignatureHelp]
  def isConfigurationSupported(config: ScastieMetalsOptions): EitherT[F, FailureType, Boolean]


object ScastieMetalsImpl:
  def instance[F[_]: Async]: ScastieMetals[F] = new ScastieMetals[F] {
    private val dispatcher: MetalsDispatcher = new MetalsDispatcher()

    def complete(request: LSPRequestDTO): EitherT[F, FailureType, CompletionList] =
      dispatcher.getCompiler(request.options).flatMap(_.complete(request.offsetParams))

    def completionInfo(request: CompletionInfoRequest): EitherT[F, FailureType, String] =
      dispatcher.getCompiler(request.options).flatMap(_.completionItemResolve(request.completionItem))

    def hover(request: LSPRequestDTO): EitherT[F, FailureType, Hover] =
      dispatcher.getCompiler(request.options).flatMap(_.hover(request.offsetParams))

    def signatureHelp(request: LSPRequestDTO): EitherT[F, FailureType, SignatureHelp] =
      dispatcher.getCompiler(request.options).flatMap(_.signatureHelp(request.offsetParams))

    def isConfigurationSupported(config: ScastieMetalsOptions): EitherT[F, FailureType, Boolean] =
      dispatcher.getCompiler(config).map(_ => true)
  }

