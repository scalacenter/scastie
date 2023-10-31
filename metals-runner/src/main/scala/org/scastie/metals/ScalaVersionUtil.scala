package org.scastie.metals

import coursier.Versions

import java.io.File

import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Try
import scala.util.control.NonFatal
import coursier.cache._
import coursier.util._
import coursier.core._
import coursier.core.{Versions as CoreVersions}
import io.circe.Codec
import io.circe.generic.semiauto._
import java.nio.file.Files
import io.circe.parser._
import io.circe.Decoder
import cats.syntax.all._
import org.scastie.buildinfo.BuildInfo
import org.scastie.metals.ScalaVersionErrors._
import org.scastie.api.FailureType


object ScalaVersionErrors {
  class ScalaVersionError(val message: String, cause: Option[Throwable] = None)

  object ScalaVersionError {
    private lazy val defaultScalaVersions =
      Seq(BuildInfo.latest212, BuildInfo.latest213, BuildInfo.latest3)

    lazy val getTheGeneralErrorInfo: String =
      s"""
         |You can only choose one of the 3.x, 2.13.x, and 2.12.x. versions.
         |The latest supported stable versions are ${defaultScalaVersions.mkString(", ")}.
         |In addition, you can request compilation with the last nightly versions of Scala,
         |by passing the 2.nightly, 2.12.nightly, 2.13.nightly, or 3.nightly arguments.
         |Specific Scala 2 or Scala 3 nightly versions are also accepted.
         |""".stripMargin
  }

  final class NoValidScalaVersionFoundError(val foundVersions: Seq[String]) extends ScalaVersionError(
      s"Cannot find a valid matching Scala version among ${foundVersions.mkString(", ")}"
  )

  final class InvalidBinaryScalaVersionError(val invalidBinaryVersion: String)
    extends ScalaVersionError(s"Cannot find matching Scala version for '$invalidBinaryVersion")

  final class UnsupportedScalaVersionError(val binaryVersion: String)
    extends ScalaVersionError(s"Unsupported Scala version: $binaryVersion")

  final class UnspecifiedScalaVersionError() extends ScalaVersionError(s"Unspecified Scala version")
}

object ScalaVersionUtil {

  val scala2Library = Module(Organization("org.scala-lang"), ModuleName("scala-library"), Map.empty)
  val scala3Library = Module(Organization("org.scala-lang"), ModuleName("scala3-library_3"), Map.empty)
  val scala212Nightly       = "2.12.nightly"
  val scala213Nightly       = List("2.13.nightly", "2.nightly")
  val scala3Nightly         = "3.nightly"

  val scala2NightlyRegex         = raw"""2\.(\d+)\.(\d+)-bin-[a-f0-9]*""".r
  val scala3NightlyNicknameRegex = raw"""3\.([0-9]*)\.nightly""".r

  extension (cache: FileCache[Task]) {
    def fileWithTtl0(artifact: Artifact): Either[ArtifactError, File] =
      cache.logger.use {
        try cache.withTtl(0.seconds).file(artifact).run.unsafeRun()(cache.ec)
        catch {
          case NonFatal(e) => throw new Exception(e)
        }
      }

    def versions(
      module: Module,
      repositories: Seq[Repository] = Seq.empty,
      ttl: Option[Duration] = None
    ): Versions.Result =
      val cacheWithTtl = ttl.map(cache.withTtl).getOrElse(cache)
      cacheWithTtl.logger.use {
        Versions(cacheWithTtl)
          .withModule(module)
          .addRepositories(repositories: _*)
          .result()
          .unsafeRun()(cacheWithTtl.ec)
      }
    def versionsWithTtl0(
      module: Module,
      repositories: Seq[Repository] = Seq.empty
    ): Versions.Result = versions(module, repositories, Some(0.seconds))
  }

  extension (versionsResult: Versions.Result) {
    def verify(versionString: String): Either[ScalaVersionError, Unit] =
      if versionsResult.versions.available.contains(versionString) then Right(())
      else Left(new NoValidScalaVersionFoundError(versionsResult.versions.available))
  }


  object GetNightly {

    private object Scala2Repo {
      final case class ScalaVersion(name: String, lastModified: Long)
      final case class ScalaVersionsMetaData(repo: String, children: List[ScalaVersion])

      implicit val scalaVersionCodec: Decoder[ScalaVersion] = deriveDecoder[ScalaVersion]
      implicit val scalaVersionsMetaDataCodec: Decoder[ScalaVersionsMetaData] = deriveDecoder[ScalaVersionsMetaData]
    }

    private def downloadScala2RepoPage(cache: FileCache[Task]): Either[ScalaVersionError, String] =
      val scala2NightlyRepo =
        "https://scala-ci.typesafe.com/ui/api/v1/ui/nativeBrowser/scala-integration/org/scala-lang/scala-compiler"
      val artifact = Artifact(scala2NightlyRepo).withChanging(true)
      val res = cache.fileWithTtl0(artifact).left.map { err =>
        val msg =
          """|Unable to compute the latest Scala 2 nightly version.
             |Throws error during downloading web page repository for Scala 2.""".stripMargin
        new ScalaVersionError(msg, cause = err.getCause.some)
      }
      res.map(file => Files.readString(file.toPath))


    def scala2(versionPrefix: String, cache: FileCache[Task]): Either[ScalaVersionError, String] =
      val sortedVersions = for
        webPageScala2Repo <- downloadScala2RepoPage(cache)
        scala2Repo        <- decode[Scala2Repo.ScalaVersionsMetaData](webPageScala2Repo).left.map(err =>
          new ScalaVersionError(s"Unable to decode Scala 2 nightly repository page: ${err.getMessage}", err.getCause.some))
      yield
        scala2Repo.children
          .filter(_.name.startsWith(versionPrefix))
          .filterNot(_.name.contains("pre"))
          .sortBy(_.lastModified)

      sortedVersions.flatMap(_.lastOption.map(_.name).toRight:
        val msg = s"""|Unable to compute the latest Scala $versionPrefix nightly version.
                      |Pass explicitly full Scala 2 nightly version.""".stripMargin
        new ScalaVersionError(msg, cause = None)
      )

    /** @return
      *   Either a BuildException or the calculated (ScalaVersion, ScalaBinaryVersion) tuple
      */
    def scala3X(
      threeSubBinaryNum: String,
      cache: FileCache[Task]
    ): Either[ScalaVersionError, String] = {
      val res = cache.versionsWithTtl0(scala3Library)
        .versions.available.filter(_.endsWith("-NIGHTLY"))

      val threeXNightlies = res.filter(_.startsWith(s"3.$threeSubBinaryNum.")).map(Version(_))
      if (threeXNightlies.nonEmpty) Right(threeXNightlies.max.repr)
      else Left(new NoValidScalaVersionFoundError(res))
    }

    /** @return
      *   Either a BuildException or the calculated (ScalaVersion, ScalaBinaryVersion) tuple
      */
    def scala3(cache: FileCache[Task]): Either[ScalaVersionError, String] =
      latestScalaVersionFrom(
        cache.versionsWithTtl0(scala3Library).versions,
        "latest Scala 3 nightly build"
      )

    private def latestScalaVersionFrom(versions: CoreVersions, desc: String): Either[ScalaVersionError, String] =
      versions.latest(coursier.core.Latest.Release) match {
        case Some(versionString) => Right(versionString)
        case None =>
          val msg =
            s"Unable to find matching version for $desc in available version: ${versions.available.mkString(", ")}. " +
              "This error may indicate a network or other problem accessing repository."
          Left(new ScalaVersionError(msg))
      }

  }

  object CheckNightly {

    def scala2(
      versionString: String,
      cache: FileCache[Task]
    ): Either[ScalaVersionError, Unit] =
      cache.versionsWithTtl0(scala2Library, Seq(coursier.Repositories.scalaIntegration))
        .verify(versionString)

    def scala3(
      versionString: String,
      cache: FileCache[Task]
    ): Either[ScalaVersionError, Unit] =
      cache.versionsWithTtl0(scala3Library).verify(versionString)
  }

  def validateNonStable(
    scalaVersionStringArg: String,
    cache: FileCache[Task],
    repositories: Seq[Repository]
  ): Either[ScalaVersionError, String] = {
    val versionPool =
      ScalaVersionUtil.allMatchingVersions(Some(scalaVersionStringArg), cache, repositories)

    if (versionPool.contains(scalaVersionStringArg))
      if (isSupportedVersion(scalaVersionStringArg))
        Right(scalaVersionStringArg)
      else
        Left(new ScalaVersionError(scalaVersionStringArg))
    else
      Left(new InvalidBinaryScalaVersionError(scalaVersionStringArg))
  }

  private def isFullScalaVersion(sv: String): Boolean =
    sv.count(_ == '.') >= 2 && !sv.endsWith(".")

  def validateStable(
    scalaVersionStringArg: String,
    cache: FileCache[Task],
    repositories: Seq[Repository]
  ): Either[ScalaVersionError, String] = {
    val versionPool =
      ScalaVersionUtil.allMatchingVersions(Some(scalaVersionStringArg), cache, repositories)
        .filter(ScalaVersionUtil.isStable)

    val prefix =
      if (isFullScalaVersion(scalaVersionStringArg)) scalaVersionStringArg
      else if (scalaVersionStringArg.endsWith(".")) scalaVersionStringArg
      else scalaVersionStringArg + "."

    val matchingStableVersions = versionPool.filter(_.startsWith(prefix)).map(Version(_))
    if (matchingStableVersions.isEmpty)
      Left(new InvalidBinaryScalaVersionError(scalaVersionStringArg))
    else {
      val supportedMatchingStableVersions =
        matchingStableVersions.filter(v => isSupportedVersion(v.repr))

      supportedMatchingStableVersions.find(_.repr == scalaVersionStringArg) match {
        case Some(v) => Right(v.repr)
        case None if supportedMatchingStableVersions.nonEmpty =>
          Right(supportedMatchingStableVersions.max.repr)
        case _ => Left(
            new UnsupportedScalaVersionError(scalaVersionStringArg)
          )
      }
    }
  }

  private def isSupportedVersion(version: String): Boolean =
    version.startsWith("2.12.") || version.startsWith("2.13.") || version.startsWith("3.")

  def isScala2Nightly(version: String): Boolean =
    scala2NightlyRegex.unapplySeq(version).isDefined
    || (scala212Nightly +: scala213Nightly).contains(version)

  def isScala3Nightly(version: String): Boolean =
    version.startsWith("3") && version.endsWith("-NIGHTLY")

  def isStable(version: String): Boolean =
    !version.exists(_.isLetter)

  def allMatchingVersions(
    maybeScalaVersionArg: Option[String],
    cache: FileCache[Task],
    repositories: Seq[Repository]
  ): Seq[String] = {

    val modules =
      if (maybeScalaVersionArg.contains("2") || maybeScalaVersionArg.exists(_.startsWith("2.")))
        Seq(scala2Library)
      else if (
        maybeScalaVersionArg.contains("3") || maybeScalaVersionArg.exists(_.startsWith("3."))
      )
        Seq(scala3Library)
      else
        Seq(scala2Library, scala3Library)

    modules
      .flatMap { mod =>
        val versions = cache.logger.use {
          try Versions(cache)
              .withModule(mod)
              .addRepositories(repositories: _*)
              .result()
              .unsafeRun()(cache.ec)
          catch {
            case NonFatal(e) => throw new Exception(e)
          }
        }
        versions.versions.available
      }
      .distinct
  }

  extension (sv: String) {
    def asVersion: Version = Version(sv)
  }
}
