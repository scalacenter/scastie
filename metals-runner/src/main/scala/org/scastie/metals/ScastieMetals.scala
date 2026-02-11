package org.scastie.metals

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.all._
import com.evolutiongaming.scache.Cache
import com.evolutiongaming.scache.ExpiringCache
import org.eclipse.lsp4j._
import org.scastie.api._

trait ScastieMetals:
  def complete(request: LSPRequestDTO): EitherT[IO, FailureType, ScalaCompletionList]
  def completionInfo(request: CompletionInfoRequest): EitherT[IO, FailureType, String]
  def hover(request: LSPRequestDTO): EitherT[IO, FailureType, Hover]
  def signatureHelp(request: LSPRequestDTO): EitherT[IO, FailureType, SignatureHelp]
  def diagnostics(request: LSPRequestDTO): EitherT[IO, FailureType, Set[Problem]]
  def isConfigurationSupported(config: ScastieMetalsOptions): EitherT[IO, FailureType, Boolean]

object ScastieMetalsImpl:

  def instance(cache: Cache[IO, (String, ScastieMetalsOptions), ScastiePresentationCompiler]): ScastieMetals =
    new ScastieMetals {

      private val dispatcher: MetalsDispatcher = new MetalsDispatcher(cache)

      /* Extracts userUuid from request. Returns error if not present. */
      private def getUserUuid(clientUuid: Option[String]): EitherT[IO, FailureType, String] =
        EitherT.fromOption[IO](clientUuid, PresentationCompilerFailure("Request does not contain client UUID"))

      def complete(request: LSPRequestDTO): EitherT[IO, FailureType, ScalaCompletionList] =
        for
          userUuid <- getUserUuid(request.clientUuid)
          compiler <- dispatcher.getCompiler(userUuid, request.options)
          result   <- EitherT.liftF(compiler.complete(request.offsetParams))
        yield result

      def completionInfo(request: CompletionInfoRequest): EitherT[IO, FailureType, String] =
        for
          userUuid <- getUserUuid(request.clientUuid)
          compiler <- dispatcher.getCompiler(userUuid, request.options)
          result   <- EitherT.liftF(compiler.completionItemResolve(request.completionItem))
        yield result

      def hover(request: LSPRequestDTO): EitherT[IO, FailureType, Hover] =
        for
          userUuid <- getUserUuid(request.clientUuid)
          result   <- dispatcher.getCompiler(userUuid, request.options) >>= (_.hover(request.offsetParams))
        yield result

      def signatureHelp(request: LSPRequestDTO): EitherT[IO, FailureType, SignatureHelp] =
        for
          userUuid <- getUserUuid(request.clientUuid)
          compiler <- dispatcher.getCompiler(userUuid, request.options)
          result   <- EitherT.liftF(compiler.signatureHelp(request.offsetParams))
        yield result

      def diagnostics(request: LSPRequestDTO): EitherT[IO, FailureType, Set[Problem]] =
        for
          userUuid <- getUserUuid(request.clientUuid)
          compiler <- dispatcher.getCompiler(userUuid, request.options)
          result   <- EitherT.liftF(compiler.diagnostics(request.offsetParams))
        yield result

      def isConfigurationSupported(config: ScastieMetalsOptions): EitherT[IO, FailureType, Boolean] =
        dispatcher.areDependenciesSupported(config) >>=
          (_ => dispatcher.checkConfiguration(config))

    }
