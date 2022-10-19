package scastie.metals

import ch.epfl.scala.bsp4j
import ch.epfl.scala.bsp4j.BspConnectionDetails
import ch.epfl.scala.bsp4j.BuildServer
import ch.epfl.scala.bsp4j.ScalaBuildServer
import ch.epfl.scala.bsp4j.ScalaBuildTarget
import ch.epfl.scala.bsp4j._
import com.google.gson.Gson
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.jsonrpc.Launcher
import cats.syntax.all._
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.jdk.FutureConverters._
import scala.meta.internal.metals.CompilerOffsetParams
import scala.meta.internal.metals.MtagsBinaries
import scala.meta.internal.process.SystemProcess
import scala.meta.io.AbsolutePath
import scala.util.Try
import com.olegych.scastie.api.ScalaDependency
import com.olegych.scastie.api.ScalaTarget
import scala.meta.internal.metals.Embedded
import coursierapi.Dependency
import coursierapi.Fetch
import coursierapi.ResolutionParams
import scala.collection.concurrent.TrieMap
import org.jline.reader.Editor
import scala.meta.internal.pc.ScalaPresentationCompiler
import scala.meta.internal.metals.MtagsResolver
import scala.meta.pc.PresentationCompiler
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.SignatureHelp
import cats.effect._
import cats.syntax.all._
import cats.data.EitherT
import java.util.concurrent.CompletableFuture
import java.util.Optional
import scala.meta.inputs.Input.VirtualFile
import scala.reflect.internal.util.SourceFile
import scala.reflect.internal.util.BatchSourceFile
import com.olegych.scastie.api._
import scastie.metals.DTOExtensions.toOffsetParams
import org.eclipse.lsp4j.CompletionItem
import JavaConverters._
import scala.meta.internal.pc.CompletionItemData


case class ScastiePresentationCompiler(pc: PresentationCompiler, metalsWorkingDirectory: Path) {
  def complete[F[_]: Async](offsetParams: ScastieOffsetParams): F[CompletionList] =
    Async[F].fromFuture(
      pc.complete(offsetParams.toOffsetParams)
    .asScala.pure)

  def completionItemResolve[F[_]: Async](completionItem: CompletionItemDTO)(implicit ec: ExecutionContext): F[String] =
    completionItem.symbol.map { symbol =>
      val completionItemJ = CompletionItem(completionItem.label)
      completionItemJ.setDetail(completionItem.detail)
      val doc = pc.completionItemResolve(completionItemJ, symbol)
        .asScala
        .map(cmp => Option(cmp.getDocumentation()).map(_.asScala.map(_.getValue()).merge).getOrElse(""))
      Async[F].fromFuture(doc.pure)
    }.getOrElse(Async[F].fromFuture(Future("").pure))

  def hover[F[_]: Async](offsetParams: ScastieOffsetParams)(implicit ec: ExecutionContext): EitherT[F, String, Hover] =
    val javaHover = pc.hover(offsetParams.toOffsetParams)
      .asScala
      .map(_.toScala.toRight("There is no hover for given position"))
    EitherT(Async[F].fromFuture(javaHover.pure))

  def signatureHelp[F[_]: Async](offsetParams: ScastieOffsetParams): F[SignatureHelp] =
    Async[F].fromFuture(pc.signatureHelp(offsetParams.toOffsetParams).asScala.pure)

}
