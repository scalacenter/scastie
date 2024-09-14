package org.scastie.metals

import scala.jdk.CollectionConverters._
import com.virtuslab.using_directives.custom.utils.Source
import com.virtuslab.using_directives.reporter.PersistentReporter
import com.virtuslab.using_directives.custom.Parser
import com.virtuslab.using_directives.custom.utils.ast.UsingDef
import com.virtuslab.using_directives.reporter.ConsoleReporter
import com.virtuslab.using_directives.custom.SimpleCommentExtractor
import org.scastie.buildinfo.BuildInfo
import com.virtuslab.using_directives.custom.utils.ast.StringLiteral
import org.scastie.api._
import com.virtuslab.using_directives.UsingDirectivesProcessor
import coursier.cache.Cache
import cats.effect.IO
import coursier.Repositories
import coursier.util.Task
import coursier.cache.FileCache
import org.scastie.metals.ScalaVersionErrors._
import cats.syntax.all._



object ScalaCliParser:
  val processor = UsingDirectivesProcessor()

  def scala212Nightly       = "2.12.nightly"
  def scala213Nightly       = List("2.13.nightly", "2.nightly")
  def scala3Nightly         = "3.nightly"

  private val cache = FileCache[Task]
  private val repositories = Seq(coursier.Repositories.scalaIntegration, coursier.Repositories.central)

  def getScalaTarget(code: String): Either[FailureType, ScastieMetalsOptions] =
    val reporter = new PersistentReporter()
    val processor = new UsingDirectivesProcessor(reporter)

    val usedDirectives = processor
      .extract(code.toCharArray)
      .asScala
      .head

    val directiveMap = usedDirectives
      .getFlattenedMap
      .asScala
      .toSeq
      .map {
        case (k, l) =>
          (k.getPath.asScala.mkString("."), l.asScala.toSeq.map(_.toString))
      }
      .toMap

    val scalaVersionDirective = directiveMap.get("scala").map(_.headOption)

    val extractedScalaVersion: Either[ScalaVersionError, String] = scalaVersionDirective match {
      case Some(None) =>
        Left(new UnspecifiedScalaVersionError())
      case Some(Some(svInput)) =>
        svInput match {
          case sv if sv == ScalaVersionUtil.scala3Nightly =>
            ScalaVersionUtil.GetNightly.scala3(cache)
          case ScalaVersionUtil.scala3NightlyNicknameRegex(threeSubBinaryNum) =>
            ScalaVersionUtil.GetNightly.scala3X(threeSubBinaryNum, cache)
          case vs if ScalaVersionUtil.scala213Nightly.contains(vs) =>
            ScalaVersionUtil.GetNightly.scala2("2.13", cache)
          case sv if sv == ScalaVersionUtil.scala212Nightly =>
            ScalaVersionUtil.GetNightly.scala2("2.12", cache)
          case versionString if ScalaVersionUtil.isScala3Nightly(versionString) =>
            ScalaVersionUtil.CheckNightly.scala3(versionString, cache)
              .map(_ => versionString)
          case versionString if ScalaVersionUtil.isScala2Nightly(versionString) =>
            ScalaVersionUtil.CheckNightly.scala2(versionString, cache)
              .map(_ => versionString)
          case versionString if versionString.exists(_.isLetter) =>
            ScalaVersionUtil.validateNonStable(versionString, cache, repositories)
          case versionString =>
            ScalaVersionUtil.validateStable(versionString, cache, repositories)
        }
      case None => BuildInfo.latestNext.asRight
    }

    val maybeToolkitVersion = directiveMap.get("toolkit").map(_.headOption).flatten.map {
      case "latest" => "latest.stable"
      case other => other
    }

    extractedScalaVersion.leftMap(err => InvalidScalaVersion(err.message)).map: scalaVersion =>

      val scalaTarget = ScalaCli(scalaVersion)
      val dependenciesDirectives = (directiveMap.get("dep")  ++ directiveMap.get("lib")).flatten
      val toolkitDependency = maybeToolkitVersion.map(ScalaDependency("org.scala-lang", "toolkit", scalaTarget, _))

      // TODO add runtime lib and scalac options

      val dependencies = dependenciesDirectives.map(_.split(":").toList).collect:
        // "groupId::artifact:version"
        case groupId :: "" :: artifactId :: version :: Nil => ScalaDependency(groupId, artifactId, scalaTarget, version)
        // "groupId::artifact::version"
        case groupId :: "" :: artifactId :: "" :: version :: Nil => ScalaDependency(groupId, artifactId, scalaTarget, version)
        // "groupId:artifact:version"
        case groupId :: artifactId :: version :: Nil => ScalaDependency(groupId, artifactId, scalaTarget, version, isAutoResolve = false)

      ScastieMetalsOptions(dependencies.toSet ++ toolkitDependency, scalaTarget, code)

