package org.scastie.metals

import cats.data.EitherT
import cats.data.OptionT
import cats.effect.Async
import cats.syntax.all._
import com.evolutiongaming.scache.Cache
import org.scastie.api._
import org.eclipse.lsp4j._
import cats.effect.IO
import com.evolutiongaming.scache.ExpiringCache
import scala.concurrent.duration.*

trait ScastieMetals:
  def complete(request: LSPRequestDTO): EitherT[IO, FailureType, ScalaCompletionList]
  def completionInfo(request: CompletionInfoRequest): EitherT[IO, FailureType, String]
  def hover(request: LSPRequestDTO): EitherT[IO, FailureType, Hover]
  def signatureHelp(request: LSPRequestDTO): EitherT[IO, FailureType, SignatureHelp]
  def isConfigurationSupported(config: ScastieMetalsOptions): EitherT[IO, FailureType, Boolean]

object ScastieMetalsImpl:

  def instance(cache: Cache[IO, ScastieMetalsOptions, ScastiePresentationCompiler]): ScastieMetals =
    new ScastieMetals {
      private val dispatcher: MetalsDispatcher = new MetalsDispatcher(cache)

      def complete(request: LSPRequestDTO): EitherT[IO, FailureType, ScalaCompletionList] =
        (dispatcher.getCompiler(request.options) >>= (_.complete(request.offsetParams)))

      def completionInfo(request: CompletionInfoRequest): EitherT[IO, FailureType, String] =
        dispatcher.getCompiler(request.options) >>= (_.completionItemResolve(request.completionItem))

      def hover(request: LSPRequestDTO): EitherT[IO, FailureType, Hover] =
        dispatcher.getCompiler(request.options).flatMapF(_.hover(request.offsetParams))

      def signatureHelp(request: LSPRequestDTO): EitherT[IO, FailureType, SignatureHelp] =
        dispatcher.getCompiler(request.options) >>= (_.signatureHelp(request.offsetParams))

      def isConfigurationSupported(config: ScastieMetalsOptions): EitherT[IO, FailureType, Boolean] =
          dispatcher.areDependenciesSupported(config) >>=
            (_ => dispatcher.getCompiler(config).map(_ => true))

    }
