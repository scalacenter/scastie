import sbt._
import Keys._

import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import org.scalajs.sbtplugin.cross.CrossProject
import sbtbuildinfo.BuildInfoPlugin
import sbtbuildinfo.BuildInfoPlugin.autoImport._

import System.{lineSeparator => nl}

import java.util.Properties
import java.nio.file._
import java.io.FileInputStream

/*
This code is shared between the build and the "build-build".
it allows us to use the api project at the build level
to generate the docker image for the different
configuration matrix
 */
object SbtShared {
  val sbt210 = "2.10.6"
  val latest211 = "2.11.11"
  val latest212 = "2.12.3"
  val latest213 = "2.13.0-M1" // waiting for play-json for M2
  val currentScalaVersion = latest212

  val latestScalaJs = "0.6.19"
  val latestDotty = "0.3.0-RC1"

  // sbt-ensime 1.12.14 creates .ensime with 2.0.0-M4 server jar
  val latestSbtEnsime = "1.12.14"
  val latestEnsime = "2.0.0-M4"
  val latestCoursier = "1.0.0-RC10"

  val sbtVersion = "0.13.16"

  val runtimeProjectName = "runtime-scala"

  def gitIsDirty(): Boolean = {
    Process("git diff-files --quiet").! == 1
  }

  def gitHash(): String = {
    import sys.process._
    if (!sys.env.contains("CI")) {

      val indexState =
        if (gitIsDirty()) "-dirty"
        else ""

      Process("git rev-parse --verify HEAD").lines.mkString("") + indexState
    } else "CI"
  }

  val gitHashNow = gitHash()
  val gitIsDirtyNow = gitIsDirty()

  val versionNow = {
    val base = "0.26.0"
    if (gitIsDirtyNow)
      base + "-SNAPSHOT"
    else {
      val hash = gitHashNow
      s"$base+$hash"
    }
  }

  val playJsonVersion = "2.6.2"

  val scalajsDomVersion = "0.9.3"

  val playJson =
    libraryDependencies += "com.typesafe.play" %%% "play-json" % playJsonVersion

  lazy val remapSourceMap =
    scalacOptions ++= {
      val ver = version.value
      val fromScastie = (baseDirectory in LocalRootProject).value.toURI.toString
      val toScastie =
        s"https://raw.githubusercontent.com/scalacenter/scastie/$gitHashNow"

      Map(fromScastie -> toScastie).map {
        case (from, to) =>
          s"-P:scalajs:mapSourceURI:$from->$to"
      }.toList
    }

  lazy val baseSettings = Seq(
    scalaVersion := currentScalaVersion,
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked"
    ),
    console := (console in Test).value,
    scalacOptions in (Test, console) -= "-Ywarn-unused-import",
    scalacOptions in (Compile, consoleQuick) -= "-Ywarn-unused-import"
  ) ++ orgSettings

  lazy val orgSettings = Seq(
    organization := "org.scastie",
    version := versionNow
  )

  def crossDir(projectId: String) = file(".cross/" + projectId)
  def dash(name: String) = name.replaceAllLiterally(".", "-")

  /* api is for the communication between sbt <=> server <=> frontend */
  def apiProject(scalaV: String, fromSbt: Boolean = false) = {
    val projectName = "api"
    val projectId0 =
      if (scalaV != currentScalaVersion) {
        s"$projectName-${dash(scalaV)}"
      } else projectName

    val extra =
      if (fromSbt) "-sbt"
      else ""

    val projectId = projectId0 + extra

    def src(config: String): Def.Initialize[File] = Def.setting {
      val base0 = (baseDirectory in ThisBuild).value

      val base =
        if (fromSbt) base0.getParentFile
        else base0

      base / projectName / "src" / config / "scala"
    }

    def readSbtVersion(base: Path): String = {
      val sbtPropertiesFileName = "build.properties"
      val projectFolder = "project"

      val to = Paths.get(projectFolder, sbtPropertiesFileName)

      val guess1 = base.resolve(to)
      val guess2 = base.getParent.resolve(to)

      val sbtPropertiesFile =
        if (Files.isRegularFile(guess1)) guess1
        else if (Files.isRegularFile(guess2)) guess2
        else {
          sys.error(
            s"cannot find $sbtPropertiesFileName in $guess1 and $guess2"
          )
        }

      val prop = new Properties()
      val input = new FileInputStream(sbtPropertiesFile.toFile)
      prop.load(input)
      input.close()

      val res = prop.getProperty("sbt.version")
      assert(res != null)

      res
    }

    CrossProject(id = projectId,
                 base = crossDir(projectId),
                 crossType = CrossType.Pure)
      .settings(baseSettings)
      .settings(
        buildInfoKeys := Seq[BuildInfoKey](
          organization,
          version,
          "runtimeProjectName" -> runtimeProjectName,
          "defaultScalaVersion" -> latest212,
          "defaultScalaJsVersion" -> latestScalaJs,
          "defaultDottyVersion" -> latestDotty,
          "latestCoursier" -> latestCoursier,
          "latestSbtEnsime" -> latestSbtEnsime,
          "sbtVersion" -> readSbtVersion(
            (baseDirectory in ThisBuild).value.toPath
          ),
          BuildInfoKey.action("gitHash") { gitHashNow }
        ),
        buildInfoPackage := "com.olegych.scastie.buildinfo",
        scalaVersion := scalaV,
        moduleName := projectName,
        unmanagedSourceDirectories in Compile += src("main").value,
        unmanagedSourceDirectories in Test += src("test").value
      )
      .settings(playJson)
      .jsSettings(
        test := {},
        libraryDependencies += "org.scala-js" %%% "scalajs-dom" % scalajsDomVersion
      )
      .jsSettings(remapSourceMap)
      .enablePlugins(BuildInfoPlugin)
  }
}
