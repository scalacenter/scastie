package scastie.metals

import cats.effect._
import cats.syntax.all._
import org.eclipse.lsp4j._
import cats.data.EitherT
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits._
import cats.instances.future._
import com.olegych.scastie.api._

trait ScastieMetals[F[_]]:
  def complete(request: LSPRequestDTO): EitherT[F, String, CompletionList]
  def completionInfo(request: CompletionInfoRequest): EitherT[F, String, String]
  def hover(request: LSPRequestDTO): EitherT[F, String, Hover]
  def signatureHelp(request: LSPRequestDTO): EitherT[F, String, SignatureHelp]


object ScastieMetalsImpl:
  def instance[F[_]: Async]: ScastieMetals[F] = new ScastieMetals[F] {
    private val dispatcher: MetalsDispatcher = new MetalsDispatcher()

    def complete(request: LSPRequestDTO): EitherT[F, String, CompletionList] =
      EitherT(dispatcher.getCompiler(request.options).traverse(_.complete(request.offsetParams)))

    def completionInfo(request: CompletionInfoRequest): EitherT[F, String, String] =
      EitherT(dispatcher.getCompiler(request.options).traverse(_.completionItemResolve(request.completionItem)))

    def hover(request: LSPRequestDTO): EitherT[F, String, Hover] =
      EitherT.fromEither(dispatcher.getCompiler(request.options)).flatMap(_.hover(request.offsetParams))

    def signatureHelp(request: LSPRequestDTO): EitherT[F, String, SignatureHelp] =
      EitherT(dispatcher.getCompiler(request.options).traverse(_.signatureHelp(request.offsetParams)))
  }

