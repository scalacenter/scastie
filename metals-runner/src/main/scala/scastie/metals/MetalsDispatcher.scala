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

class MetalsDispatcher() {
  val compiler: TrieMap[ScastieMetalsOptions, ScastiePresentationCompiler] = TrieMap()
  private val mtagsResolver = MtagsResolver.default()
  private val metalsWorkingDirectory = Files.createTempDirectory("scastie-metals")

  def getCompiler(configuration: ScastieMetalsOptions): Either[String, ScastiePresentationCompiler] = {
    mtagsResolver
      .resolve(configuration.scalaTarget.scalaVersion)
      .toRight("Mtags couldn't be resolved")
      .map(mtags => compiler.getOrElseUpdate(configuration, initializeCompiler(configuration, mtags)))
  }

  def isSupportedScalaVersion(scalaTarget: ScalaTarget) = mtagsResolver.isSupportedScalaVersion(scalaTarget.scalaVersion)

  private def initializeCompiler(configuration: ScastieMetalsOptions, mtags: MtagsBinaries): ScastiePresentationCompiler = {
    val classpath = getDependencyClasspath(configuration.dependencies) ++ getCompilerClasspath(configuration.scalaTarget)
    val scalaVersion = configuration.scalaTarget.scalaVersion
    val pc = PresentationCompilers.createPresentationCompiler(classpath.toSeq, scalaVersion, mtags)
    ScastiePresentationCompiler(pc, metalsWorkingDirectory)
  }

  private def artifactWithBinaryVersion(artifact: String, target: ScalaTarget) =
    s"${artifact}_${target.binaryScalaVersion}"

  private def getCompilerClasspath(scalaTarget: ScalaTarget): Set[Path] = {
    Embedded.scalaLibrary(scalaTarget.scalaVersion).toSet
  }

  private def getDependencyClasspath(dependencies: Set[ScalaDependency]): Set[Path] = {
    val dep = dependencies.map {
      case ScalaDependency(groupId, artifact, target, version) =>
        Dependency.of(groupId, artifactWithBinaryVersion(artifact, target), version)
    }.toSeq

    // add cache for coursier directory
    Fetch
      .create()
      .addRepositories(Embedded.repositories: _*)
      .withDependencies(dep: _*)
      .fetch().asScala.map(file => Path.of(file.getPath)).toSet

  }
}
