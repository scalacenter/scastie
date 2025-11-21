package org.scastie.metals

import java.util.UUID
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
  def diagnostics(request: LSPRequestDTO): EitherT[IO, FailureType, Set[Problem]]
  def isConfigurationSupported(config: ScastieMetalsOptions): EitherT[IO, FailureType, Boolean]

object ScastieMetalsImpl:

  def instance(cache: Cache[IO, (String, ScastieMetalsOptions), ScastiePresentationCompiler]): ScastieMetals =
    new ScastieMetals {

      private val dispatcher: MetalsDispatcher = new MetalsDispatcher(cache)

      /* Extracts userUuid from request. Falls back to generating a new UUID if not present. */
      private def getUserUuid(clientUuid: Option[String]): String =
        clientUuid.getOrElse(UUID.randomUUID().toString)

      def complete(request: LSPRequestDTO): EitherT[IO, FailureType, ScalaCompletionList] =
        val userUuid = getUserUuid(request.clientUuid)
        dispatcher.getCompiler(userUuid, request.options).semiflatMap(_.complete(request.offsetParams))

      def completionInfo(request: CompletionInfoRequest): EitherT[IO, FailureType, String] =
        val userUuid = getUserUuid(request.clientUuid)
        dispatcher.getCompiler(userUuid, request.options).semiflatMap(_.completionItemResolve(request.completionItem))

      def hover(request: LSPRequestDTO): EitherT[IO, FailureType, Hover] =
        val userUuid = getUserUuid(request.clientUuid)
        dispatcher.getCompiler(userUuid, request.options) >>= (_.hover(request.offsetParams))

      def signatureHelp(request: LSPRequestDTO): EitherT[IO, FailureType, SignatureHelp] =
        val userUuid = getUserUuid(request.clientUuid)
        dispatcher.getCompiler(userUuid, request.options).semiflatMap(_.signatureHelp(request.offsetParams))

      def diagnostics(request: LSPRequestDTO): EitherT[IO, FailureType, Set[Problem]] =
        val userUuid = getUserUuid(request.clientUuid)
        dispatcher.getCompiler(userUuid, request.options).semiflatMap(_.diagnostics(request.offsetParams))

      def isConfigurationSupported(config: ScastieMetalsOptions): EitherT[IO, FailureType, Boolean] =
        /* For configuration check, we use a temporary UUID since we're just checking support */
        val tempUuid = UUID.randomUUID().toString
        dispatcher.areDependenciesSupported(config) >>=
          (_ => dispatcher.getCompiler(tempUuid, config).map(_ => true))

    }
