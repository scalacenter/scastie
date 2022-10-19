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
import com.olegych.scastie.api._
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
import com.olegych.scastie.api.ScalaTarget.Jvm
import com.olegych.scastie.api.ScalaTarget.Typelevel
import com.olegych.scastie.api.ScalaTarget.Js
import com.olegych.scastie.api.ScalaTarget.Native
import com.olegych.scastie.api.ScalaTarget.Scala3

class MetalsDispatcher() {
  private val compiler: TrieMap[ScastieMetalsOptions, ScastiePresentationCompiler] = TrieMap()
  private val mtagsResolver = MtagsResolver.default()
  private val metalsWorkingDirectory = Files.createTempDirectory("scastie-metals")
  private val supportedVersions = Set("2.12", "2.13", "3")

  def getCompiler(configuration: ScastieMetalsOptions): Either[String, ScastiePresentationCompiler] = {
    if !isSupportedVersion(configuration) then
      Left(s"Interactive features are not supported for Scala ${configuration.scalaTarget.binaryScalaVersion}.")
    else
      mtagsResolver
        .resolve(configuration.scalaTarget.scalaVersion)
        .toRight("Mtags couldn't be resolved")
        .map(mtags => compiler.getOrElseUpdate(configuration, initializeCompiler(configuration, mtags)))
  }

  def isSupportedVersion(configuration: ScastieMetalsOptions): Boolean =
    supportedVersions.contains(configuration.scalaTarget.binaryScalaVersion)

  private def initializeCompiler(configuration: ScastieMetalsOptions, mtags: MtagsBinaries): ScastiePresentationCompiler = {
    val classpath = getDependencyClasspath(configuration.dependencies) ++
      getCompilerClasspath(configuration.scalaTarget) ++
      getCompilerSources(configuration.scalaTarget)
    val scalaVersion = configuration.scalaTarget.scalaVersion
    val pc = PresentationCompilers.createPresentationCompiler(classpath.toSeq, scalaVersion, mtags)
    ScastiePresentationCompiler(pc, metalsWorkingDirectory)
  }

  private def artifactWithBinaryVersion(artifact: String, target: ScalaTarget) =
    s"${artifact}_${target.binaryScalaVersion}"

  private def getCompilerClasspath(scalaTarget: ScalaTarget): Set[Path] = {
    Embedded.scalaLibrary(scalaTarget.scalaVersion).toSet
  }

  private def getCompilerSources(scalaTarget: ScalaTarget): Set[Path] = {
    scalaTarget match
      case Jvm(scalaVersion) => Embedded.downloadScalaSources(scalaTarget.scalaVersion).toSet
      case Typelevel(scalaVersion) => Set.empty
      case Js(scalaVersion, scalaJsVersion) => Set.empty
      case Native(scalaVersion, scalaNativeVersion) => Set.empty
      case Scala3(scalaVersion) => Embedded.downloadScala3Sources(scalaTarget.scalaVersion).toSet
  }


  private def getDependencyClasspath(dependencies: Set[ScalaDependency]): Set[Path] = {
    val dep = dependencies.map {
      case ScalaDependency(groupId, artifact, target, version) =>
        Dependency.of(groupId, artifactWithBinaryVersion(artifact, target), version)
    }.toSeq

    // Fetch
    //   .create()
    //   .withRepositories(repositories.toSeq: _*)
    //   .withDependencies(dependencies: _*)
    //   .withClassifiers(classifiers.asJava)
    //   .withMainArtifacts()
    //   .fetch()
    //   .map(_.toPath)
    //   .asScala
    //   .toList

    Fetch
      .create()
      .addRepositories(Embedded.repositories: _*)
      .withDependencies(dep: _*)
      .withClassifiers(Set("sources").asJava)
      .withMainArtifacts()
      .fetch().asScala.map(file => Path.of(file.getPath)).toSet

  }
}
