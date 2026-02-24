package org.scastie.client.scalacli

import org.scastie.api._
import org.scastie.api.ScalaVersions._
import org.scastie.buildinfo.BuildInfo
import org.scastie.client.scalacli.ScalaVersionUtil._

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import japgolly.scalajs.react.callback.Callback

object ScalaCliUtils {

  private val ScalaVersionRegex = """//> *using +scala( +([^\s]+))?""".r
  private val DepRegex = """//> *using +(dep|lib) +([^\s]+)""".r
  private val ToolkitRegex = """//> *using +toolkit +([^\s]+)""".r

  def getVersionDirective(codeHeader: List[String]): Future[String] = {
    val maybeVersionDirective: Option[String] = codeHeader.collectFirst {
      case ScalaVersionRegex(_, v) =>
        Option(v).map(_.trim).filter(_.nonEmpty)
    }.flatten

    ScalaVersionUtil.resolveVersion(maybeVersionDirective.getOrElse(BuildInfo.stableNext))
  }

  def getToolkitVersion(codeHeader: List[String]): Option[String] =
    codeHeader.collectFirst {
      case ToolkitRegex(v) => if (v == "latest") "latest.stable" else v
    }

  def getDependencies(codeHeader: List[String]): Set[String] =
    codeHeader.collect {
      case DepRegex(_, dep) => dep.trim.stripPrefix("\"").stripSuffix("\"")
    }.toSet

  def parse(codeHeader: List[String]): Future[(ScalaTarget, Set[ScalaDependency])] =
    for {
      version <- getVersionDirective(codeHeader)
      dependencies = getDependencies(codeHeader)
      toolkit   = getToolkitVersion(codeHeader)

    } yield {
      val scalaTarget = ScalaCli(version)
      val toolkitDependency = toolkit.map(ScalaDependency("org.scala-lang", "toolkit", scalaTarget, _))
      val deps = dependencies.flatMap { dep =>
        dep.split(":").toList match {
            case groupId :: "" :: artifactId :: version :: Nil =>
              Some(ScalaDependency(groupId, artifactId, scalaTarget, version))
            case groupId :: "" :: artifactId :: "" :: version :: Nil =>
              Some(ScalaDependency(groupId, artifactId, scalaTarget, version))
            case groupId :: artifactId :: version :: Nil =>
              Some(ScalaDependency(groupId, artifactId, scalaTarget, version, isAutoResolve = false))
            case _ => None
          }
        }.toSet
      (scalaTarget, deps ++ toolkitDependency)
    }

  extension (inputs: BaseInputs) {
    def setTarget(newTarget: ScalaTarget): BaseInputs = {
      inputs -> newTarget match {
        case (sbtInputs: SbtInputs, newSbtScalaTarget: SbtScalaTarget) => sbtInputs.copy(target = newSbtScalaTarget)
        case (scalaCliInputs: ScalaCliInputs, newScalaCliTarget: ScalaCli) => scalaCliInputs.copy(target = newScalaCliTarget)
        case (_: ScalaCliInputs, newSbtScalaTarget: SbtScalaTarget) => inputs.convertToSbt(newSbtScalaTarget)
        case (_: SbtInputs, _: ScalaCli) => inputs.convertToScalaCli
        case _ => inputs
      }
    }

    def convertToSbt(newSbtScalaTarget: SbtScalaTarget): SbtInputs = {
      val oldInputsVersion = inputs.target.scalaVersion
      val correctScalaVersions = allVersions(newSbtScalaTarget.targetType)
      val convertedTarget = oldInputsVersion match {
        case v if correctScalaVersions.contains(v) => newSbtScalaTarget.withScalaVersion(v)
        case _ => newSbtScalaTarget
      }

      val mappedInputs = inputs.libraries.map(_.copy(target = convertedTarget))

      SbtInputs.default.copy(
        isWorksheetMode = inputs.isWorksheetMode,
        isShowingInUserProfile = false,
        code = inputs.code,
        target = convertedTarget,
        libraries = mappedInputs,
        forked = None
      )
    }

    def convertToScalaCli: ScalaCliInputs = {
      val scalaCliTarget = ScalaCli(inputs.target.scalaVersion)
      val newLibraries = inputs.libraries.map(_.copy(target = scalaCliTarget))

      val (previousDirectives, remainingCode) = inputs.code.linesIterator.span(_.startsWith("//>"))
      val directives = (scalaCliTarget.versionDirective +: newLibraries.map(_.renderScalaCli).toList).mkString("\n")
      val codeWithoutDirectives = remainingCode.mkString("\n")

      val codeWithDirectives = s"""$directives
                                  |$codeWithoutDirectives""".stripMargin

      ScalaCliInputs.default.copy(
        isWorksheetMode = inputs.isWorksheetMode,
        isShowingInUserProfile = false,
        code = codeWithDirectives,
        target = scalaCliTarget,
        libraries = newLibraries,
        forked = None
      )
    }
  }
}
