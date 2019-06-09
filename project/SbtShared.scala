import sbt._
import Keys._

import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import org.scalajs.sbtplugin.cross.CrossProject
import sbtbuildinfo.BuildInfoPlugin
import sbtbuildinfo.BuildInfoPlugin.autoImport._

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
  val sbt210 = "2.10.7"
  val latest211 = "2.11.12"
  val latest212 = "2.12.8"
  val latest213 = "2.13.0"
  val currentScalaVersion = latest212

  val latestScalaJs = "0.6.28"
  //todo allow choosing dotty version and merge com.olegych.scastie.api.ScalaTarget.sbtConfig with com.olegych.scastie.api.Inputs.sbtConfigExtra
  val latestDotty = "0.16.0-RC2"

  val latestCoursier = "1.1.0-M14-1"

  val sbtVersion = "1.2.8"

  val runtimeProjectName = "runtime-scala"

  def gitIsDirty(): Boolean = {
    import sys.process._
    "git diff-files --quiet".! == 1
  }

  def gitHash(): String = {
    import sys.process._
    if (!sys.env.contains("CI")) {

      val indexState =
        if (gitIsDirty()) "-dirty"
        else ""

      Process("git rev-parse --verify HEAD").lineStream.mkString("") + indexState
    } else "CI"
  }

  val gitHashNow = gitHash()
  val gitIsDirtyNow = gitIsDirty()

  val versionNow = {
    val base = "0.29.0"
    if (gitIsDirtyNow)
      base + "-SNAPSHOT"
    else {
      val hash = gitHashNow
      s"$base+$hash"
    }
  }

  val playJsonVersion = "2.6.9"
  val playJsonVersion213 = "2.8.0-M1"

  val scalajsDomVersion = "0.9.7"

  val playJson = libraryDependencies += {
    scalaVersion.value match {
      case v if v.startsWith("2.13") =>
        "com.typesafe.play" % "play-json_2.13.0-RC2" % playJsonVersion213
      case _ =>
        toScalaJSGroupID("com.typesafe.play") %%% "play-json" % playJsonVersion
    }
  }

  lazy val baseSettings = Seq(
    // skip scaladoc
    publishArtifact in (Compile, packageDoc) := false,
    publishArtifact in packageDoc := false,
    publishArtifact in packageSrc := false,
    sources in (Compile, doc) := Seq.empty,
    parallelExecution in Test := false,
    scalaVersion := currentScalaVersion,
    scalacOptions ++= {
      val scalaV = scalaVersion.value

      val base =
        Seq(
          "-deprecation",
          "-encoding",
          "UTF-8",
          "-feature",
          "-unchecked"
        )

      if (scalaV == sbt210) base
      else {
        base ++ Seq(
          "-Yrangepos",
        )
      }

    },
    console := (console in Test).value,
    scalacOptions in (Test, console) -= "-Ywarn-unused-import",
    scalacOptions in (Compile, consoleQuick) -= "-Ywarn-unused-import"
  ) ++ orgSettings

  lazy val orgSettings = Seq(
    organization := "org.scastie",
    version := versionNow
  )

  val baseJsSettings = Seq(
    scalacOptions += "-P:scalajs:sjsDefinedByDefault"
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

    CrossProject(id = projectId, base = crossDir(projectId), crossType = CrossType.Pure)
      .settings(baseSettings)
      .settings(
        buildInfoKeys := Seq[BuildInfoKey](
          organization,
          version,
          "runtimeProjectName" -> runtimeProjectName,
          "defaultScalaVersion" -> latest213,
          "defaultScalaJsVersion" -> latestScalaJs,
          "defaultDottyVersion" -> latestDotty,
          "latestCoursier" -> latestCoursier,
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
      .jsSettings(baseJsSettings)
      .jsSettings(
        test := {},
        libraryDependencies += toScalaJSGroupID("org.scala-js") %%% "scalajs-dom" % scalajsDomVersion
      )
      .enablePlugins(BuildInfoPlugin)
  }
}
