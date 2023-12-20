package scastie.metals

import cats.data.EitherT
import cats.data.OptionT
import cats.effect.Async
import cats.syntax.all._
import com.evolutiongaming.scache.Cache
import com.olegych.scastie.api._
import org.eclipse.lsp4j._

trait ScastieMetals[F[_]]:
  def complete(request: LSPRequestDTO): EitherT[F, FailureType, ScalaCompletionList]
  def completionInfo(request: CompletionInfoRequest): EitherT[F, FailureType, String]
  def hover(request: LSPRequestDTO): EitherT[F, FailureType, Hover]
  def signatureHelp(request: LSPRequestDTO): EitherT[F, FailureType, SignatureHelp]
  def isConfigurationSupported(config: ScastieMetalsOptions): EitherT[F, FailureType, Boolean]

object ScastieMetalsImpl:

  def instance[F[_]: Async](cache: Cache[F, ScastieMetalsOptions, ScastiePresentationCompiler]): ScastieMetals[F] =
    new ScastieMetals[F] {
      private val dispatcher: MetalsDispatcher[F] = new MetalsDispatcher[F](cache)

      def complete(request: LSPRequestDTO): EitherT[F, FailureType, ScalaCompletionList] =
        (dispatcher.getCompiler(request.options) >>= (_.complete(request.offsetParams)))

      def completionInfo(request: CompletionInfoRequest): EitherT[F, FailureType, String] =
        dispatcher.getCompiler(request.options) >>= (_.completionItemResolve(request.completionItem))

      def hover(request: LSPRequestDTO): EitherT[F, FailureType, Hover] =
        dispatcher.getCompiler(request.options).flatMapF(_.hover(request.offsetParams))

      def signatureHelp(request: LSPRequestDTO): EitherT[F, FailureType, SignatureHelp] =
        dispatcher.getCompiler(request.options) >>= (_.signatureHelp(request.offsetParams))

      def isConfigurationSupported(config: ScastieMetalsOptions): EitherT[F, FailureType, Boolean] =
        dispatcher.areDependenciesSupported(config) >>=
          (_ => dispatcher.getCompiler(config).map(_ => true))

    }
