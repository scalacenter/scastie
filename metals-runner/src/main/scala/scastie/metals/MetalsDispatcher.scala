package scastie.metals

import java.nio.file.Files
import java.nio.file.Path
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.meta.internal.metals.Embedded
import scala.meta.internal.metals.MtagsBinaries
import scala.meta.internal.metals.MtagsResolver

import cats.data.EitherT
import cats.effect.Async
import com.google.common.cache._
import com.olegych.scastie.api._
import com.olegych.scastie.api.ScalaTarget._
import coursierapi.{Dependency, Fetch}

class MetalsDispatcher() {

  private val cache: LoadingCache[(ScastieMetalsOptions, MtagsBinaries), ScastiePresentationCompiler] = CacheBuilder
    .newBuilder()
    .expireAfterAccess(1, java.util.concurrent.TimeUnit.MINUTES)
    .maximumSize(25)
    .build(new CacheLoader[(ScastieMetalsOptions, MtagsBinaries), ScastiePresentationCompiler] {
      override def load(key: (ScastieMetalsOptions, MtagsBinaries)): ScastiePresentationCompiler =
        initializeCompiler(key._1, key._2)
    })

  private val mtagsResolver          = MtagsResolver.default()
  private val metalsWorkingDirectory = Files.createTempDirectory("scastie-metals")
  private val supportedVersions      = Set("2.12", "2.13", "3")

  def getCompiler[F[_]: Async](
    configuration: ScastieMetalsOptions
  )(
    implicit ec: ExecutionContext
  ): EitherT[F, FailureType, ScastiePresentationCompiler] = EitherT(Async[F].pure {
    if !isSupportedVersion(configuration) then
      Left(
        PresentationCompilerFailure(
          s"Interactive features are not supported for Scala ${configuration.scalaTarget.binaryScalaVersion}."
        )
      )
    else
      mtagsResolver
        .resolve(configuration.scalaTarget.scalaVersion)
        .toRight(PresentationCompilerFailure("Mtags couldn't be resolved."))
        .map(mtags => cache.get(configuration -> mtags))
  })

  private def isSupportedVersion(configuration: ScastieMetalsOptions): Boolean =
    supportedVersions.contains(configuration.scalaTarget.binaryScalaVersion)

  private def initializeCompiler(
    configuration: ScastieMetalsOptions,
    mtags: MtagsBinaries
  ): ScastiePresentationCompiler = {
    val classpath = getDependencyClasspath(configuration.dependencies) ++
      getCompilerClasspath(configuration.scalaTarget) ++
      getCompilerSources(configuration.scalaTarget)
    val scalaVersion = configuration.scalaTarget.scalaVersion
    val pc           = PresentationCompilers.createPresentationCompiler(classpath.toSeq, scalaVersion, mtags)
    ScastiePresentationCompiler(pc, metalsWorkingDirectory)
  }

  private def artifactWithBinaryVersion(artifact: String, target: ScalaTarget) =
    s"${artifact}_${target.binaryScalaVersion}"

  private def getCompilerClasspath(scalaTarget: ScalaTarget): Set[Path] = {
    Embedded.scalaLibrary(scalaTarget.scalaVersion).toSet
  }

  private def getCompilerSources(scalaTarget: ScalaTarget): Set[Path] = {
    scalaTarget match
      case Jvm(scalaVersion)                        => Embedded.downloadScalaSources(scalaTarget.scalaVersion).toSet
      case Typelevel(scalaVersion)                  => Set.empty
      case Js(scalaVersion, scalaJsVersion)         => Set.empty
      case Native(scalaVersion, scalaNativeVersion) => Set.empty
      case Scala3(scalaVersion)                     => Embedded.downloadScala3Sources(scalaTarget.scalaVersion).toSet
  }

  private def getDependencyClasspath(dependencies: Set[ScalaDependency]): Set[Path] = {
    val dep = dependencies.map { case ScalaDependency(groupId, artifact, target, version) =>
      Dependency.of(groupId, artifactWithBinaryVersion(artifact, target), version)
    }.toSeq

    Fetch
      .create()
      .addRepositories(Embedded.repositories: _*)
      .withDependencies(dep: _*)
      .withClassifiers(Set("sources").asJava)
      .withMainArtifacts()
      .fetch()
      .asScala
      .map(file => Path.of(file.getPath))
      .toSet
  }

}
