package org.scastie.metals

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.concurrent.duration.*
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.meta.internal.metals.Embedded
import scala.meta.internal.metals.MtagsBinaries
import scala.meta.internal.metals.MtagsResolver
import scala.meta.internal.semver.SemVer

import cats.data.EitherT
import cats.data.OptionT
import cats.effect.{Async, Sync}
import cats.syntax.all._
import com.evolutiongaming.scache.{Cache, Releasable}
import org.scastie.api._
import org.scastie.api.ScalaTarget._
import coursierapi.{Dependency, Fetch, MavenRepository, Repository}
import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext
import com.typesafe.config.ConfigFactory
import cats.effect.IO

/*
 * MetalsDispatcher is responsible for managing the lifecycle of presentation compilers.
 *
 * Each metals client configuration requires separate presentation compilers
 * to support cabailities for 3rd party capabilities.
 *
 * @param cache - cache used for managing presentation compilers (keyed by userUuid and configuration)
 */
class MetalsDispatcher(cache: Cache[IO, (String, ScastieMetalsOptions), ScastiePresentationCompiler]) {
  private val logger = LoggerFactory.getLogger(getClass)

  private val config            = ConfigFactory.load().getConfig("scastie.metals")
  private val isDocker          = config.getBoolean("is-docker")
  private val lastMtags3Version = "3.3.3"

  private val mtagsResolver = new MtagsResolver.Default:
    override def hasStablePresentationCompiler(scalaVersion: String): Boolean =
      SemVer.isLaterVersion(lastMtags3Version, scalaVersion)

  private val metalsWorkingDirectory =
    if isDocker then Files.createDirectories(Paths.get("/home/scastie")) // temporal solution
    else Files.createTempDirectory("scastie-metals")

  logger.info(s"Metals working directory: $metalsWorkingDirectory")

  private val presentationCompilers = PresentationCompilers(metalsWorkingDirectory)
  private val supportedVersions     = Set("2.12", "2.13", "3")
  private val repositories          = Seq(
    Repository.central(),
    MavenRepository.of("https://central.sonatype.com/repository/maven-snapshots/")
  )

  def getMtags(scalaVersion: String)=
    for
      given ExecutionContext <- IO.executionContext
      mtags <- IO.blocking(
        mtagsResolver
          .resolve(scalaVersion)
          .toRight(PresentationCompilerFailure(s"Mtags couldn't be resolved for target: ${scalaVersion}."))
        ).recover { case err: MatchError => PresentationCompilerFailure(err.getMessage).asLeft }
    yield mtags

  /*
   * If `configuration` is supported returns either `FailureType` in case of error during its initialization
   * or fetches the `ScastiePresentationCompiler` from guava cache.
   * If the key is not present in guava cache, it is initialized
   *
   * Caching: Presentation compilers are cached by (userUuid, configuration).
   *
   * @param userUuid - unique identifier for the user (random UUID generated per session)
   * @param configuration - scastie client configuration
   * @returns `EitherT[F, FailureType, ScastiePresentationCompiler]`
   */
  def getCompiler(userUuid: String, configuration: ScastieMetalsOptions): EitherT[IO, FailureType, ScastiePresentationCompiler] =
    EitherT:
      if !isSupportedVersion(configuration) then
        IO {
          PresentationCompilerFailure(
            s"Interactive features are not supported for Scala ${configuration.scalaTarget.binaryScalaVersion}."
          ).asLeft
        }
      else
        val cacheKey = (userUuid, configuration)
        cache
          .contains(cacheKey)
          .flatMap: isCached =>
            if isCached then
              cache
                .get(cacheKey)
                .map(_.toRight(PresentationCompilerFailure("Can't extract presentation compiler from cache.")))
            else
              for
                mtags    <- EitherT(getMtags(configuration.scalaTarget.scalaVersion))
                compiler <- EitherT.right(
                  cache.getOrUpdateReleasable(cacheKey) {
                    initializeCompiler(configuration, mtags).map: newPC =>
                      Releasable(newPC, newPC.shutdown())
                  })
              yield compiler
            .value

  /*
   * Checks if given configuration is supported. Currently it is based on scala binary version.
   * We are supporting only those versions which are defined in `supportedVersions`.
   */
  private def isSupportedVersion(configuration: ScastieMetalsOptions): Boolean =
    supportedVersions.contains(configuration.scalaTarget.binaryScalaVersion)

  /*
   * Validates configuration without creating a presentation compiler.
   * Checks version support and mtags resolution.
   */
  def checkConfiguration(conf: ScastieMetalsOptions): EitherT[IO, FailureType, Boolean] =
    if !isSupportedVersion(conf) then
      EitherT.leftT(PresentationCompilerFailure(
        s"Interactive features are not supported for Scala ${conf.scalaTarget.binaryScalaVersion}."
      ))
    else
      EitherT(getMtags(conf.scalaTarget.scalaVersion)).map(_ => true)

  /*
   * This is workaround for bad scaladex search UI in scastie.
   * We must properly handle non compatibile library versions.
   * In sbt it is automatically resolved but here, we manually specify scala target.
   */
  def areDependenciesSupported(configuration: ScastieMetalsOptions): EitherT[IO, FailureType, Boolean] =
    def scalaTargetString(scalaTarget: ScalaTarget): String =
      s"${scalaTarget.scalaVersion}" ++ (if scalaTarget.isInstanceOf[Js] then "JS" else "")

    def checkScalaVersionCompatibility(scalaTarget: ScalaTarget): Boolean =
      SemVer.isCompatibleVersion(scalaTarget.scalaVersion, configuration.scalaTarget.scalaVersion)

    def checkScalaJsCompatibility(scalaTarget: ScalaTarget): Boolean =
      if configuration.scalaTarget.isInstanceOf[Js] then scalaTarget.isInstanceOf[Js]
      else true


    val misconfiguredLibraries = configuration.dependencies
      .filterNot(l => checkScalaVersionCompatibility(l.target) && checkScalaJsCompatibility(l.target))

    Option
      .when(misconfiguredLibraries.nonEmpty) {
        val errorString = misconfiguredLibraries
          .map(l =>
            s"${l.toString} dependency  binary version is: ${scalaTargetString(l.target)} while scastie is set to: ${scalaTargetString(configuration.scalaTarget)}"
          )
          .mkString("\n")
        PresentationCompilerFailure(s"Misconfigured dependencies: $errorString")
      }
      .toLeft(true)
      .toEitherT

  /*
   * Initializes the compiler with proper classpath and version
   *
   * @param configuration - scastie client configuration
   * @param mtags - binaries of mtags for specific scala version
   */
  private def initializeCompiler(
    configuration: ScastieMetalsOptions,
    mtags: MtagsBinaries
  ): IO[ScastiePresentationCompiler] = (
    getDependencyClasspath(configuration.dependencies, getScalaJsDependencies(configuration.scalaTarget)),
    getScalaLibrary(configuration.scalaTarget),
    getScalaTargetSources(configuration.scalaTarget)
  ).mapN(_ ++ _ ++ _) >>= { classpath =>
    val scalaVersion = configuration.scalaTarget.scalaVersion
    presentationCompilers
      .createPresentationCompiler(classpath.toSeq, scalaVersion, mtags)
      .flatMap(ScastiePresentationCompiler.apply)
  }

  /*
   * Maps the artifact name to proper artifact name.
   */
  private def artifactWithBinaryVersion(artifact: String, target: ScalaTarget): String = target match
    case Js(scalaVersion, scalaJsVersion) =>
      val scalaJsBinaryVersion =
        if scalaJsVersion.startsWith("1") then "1"
        else scalaJsVersion.split('.').take(2).mkString(".")

      s"${artifact}_sjs${scalaJsBinaryVersion}_${target.binaryScalaVersion}"
    case other => s"${artifact}_${target.binaryScalaVersion}"

  /*
   * Fetches scala library for given `scalaTarget`
   *
   * @param scalaTarget - scala target for scastie client configuration
   * @returns paths of downloaded files
   */
  private def getScalaLibrary(scalaTarget: ScalaTarget): IO[Set[Path]] =
    IO.blocking { Embedded.scalaLibrary(scalaTarget.scalaVersion).toSet }

  /*
   * Fetches scala sources for given `scalaTarget`
   *
   * @param scalaTarget - scala target for scastie client configuration
   * @returns paths of downloaded files
   */
  private def getScalaTargetSources(scalaTarget: ScalaTarget): IO[Set[Path]] = IO.blocking {
    if scalaTarget.scalaVersion.startsWith("3") then Embedded.downloadScala3Sources(scalaTarget.scalaVersion).toSet
    else Embedded.downloadScalaSources(scalaTarget.scalaVersion).toSet
  }

  /*
   * Fetches scalajs sources when `scalaTarget` is `scalajs`
   *
   * @param scalaTarget - scala target for scastie client configuration
   * @returns paths of downloaded files
   */
  private def getScalaJsDependencies(scalaTarget: ScalaTarget): Set[Dependency] = scalaTarget match
    case Js(scalaVersion, scalaJsVersion) if scalaVersion.startsWith("3") =>
      Set(Dependency.of("org.scala-js", "scalajs-library_2.13", scalaJsVersion))
    case Js(scalaVersion, scalaJsVersion) => Set(
        Dependency.of("org.scala-js", artifactWithBinaryVersion("scalajs-library", Scala2(scalaVersion)), scalaJsVersion)
      )
    case _ => Set.empty

  /*
   * Fetches scala dependencies classpath
   *
   * @param dependencies - scala dependencies used in scastie client configuration
   * @param extraDependencies - extra dependencies to fetch
   * @returns paths of downloaded files
   */
  private def getDependencyClasspath(
    dependencies: Set[ScalaDependency],
    extraDependencies: Set[Dependency]
  ): IO[Set[Path]] = IO.blocking {
    val dep = dependencies.map {
      case ScalaDependency(groupId, artifact, target, version, true) =>
        Dependency.of(groupId, artifactWithBinaryVersion(artifact, target), version)
      case ScalaDependency(groupId, artifact, target, version, false) =>
        Dependency.of(groupId, artifact, version)
    }.toSeq ++ extraDependencies

    Fetch
      .create()
      .addRepositories(repositories*)
      .withDependencies(dep*)
      .withClassifiers(Set("sources").asJava)
      .withMainArtifacts()
      .fetch()
      .asScala
      .map(file => Path.of(file.getPath))
      .toSet
  }

}
