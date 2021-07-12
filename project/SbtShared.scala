import sbt._
import Keys._

import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbtbuildinfo.BuildInfoPlugin
import sbtbuildinfo.BuildInfoPlugin.autoImport._
import sbtprojectmatrix.ProjectMatrixPlugin.autoImport._

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
  object ScalaVersions {
    val latest210 = "2.10.7"
    val latest211 = "2.11.12"
    val latest212 = "2.12.13"
    val latest213 = "2.13.6"
    val stable3   = "3.0.0"
    val latest3   = "3.0.1"
    val js = latest213
    val sbt = latest212
    val jvm = latest213
    val cross = List(latest210, latest211, latest212, latest213, js, sbt, jvm).distinct
  }

  object ScalaJSVersions {
    val current = "1.5.1"
  }

  val distSbtVersion = "1.5.2"

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
    val base = "0.30.0"
    if (gitIsDirtyNow)
      base + "-SNAPSHOT"
    else {
      val hash = gitHashNow
      s"$base+$hash"
    }
  }
  lazy val versionRuntime = "1.0.0-SNAPSHOT"

  lazy val orgSettings = Seq(
    organization := "org.scastie",
    version := versionNow,
  )

  lazy val baseSettings = Seq(
    // skip scaladoc
    Compile / packageDoc / publishArtifact := false,
    packageSrc / publishArtifact := false,
    Compile / doc / sources := Seq.empty,
    Test / parallelExecution := false,
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

      if (scalaV == ScalaVersions.latest210) base
      else {
        base ++ Seq(
          "-Yrangepos",
        )
      }

    },
    console := (Test / console).value,
    Test / console / scalacOptions -= "-Ywarn-unused-import",
    Compile / consoleQuick / scalacOptions -= "-Ywarn-unused-import"
  ) ++ orgSettings

  lazy val baseNoCrossSettings = baseSettings ++ Seq(
    scalaVersion := ScalaVersions.jvm,
  )

  lazy val baseJsSettings = Seq(
    test := {},
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "1.1.0",
  )

  private def readSbtVersion(base: Path): String = {
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
    val prop = new Properties() {
      new FileInputStream(sbtPropertiesFile.toFile) {
        load(this)
        close()
      }
    }
    val res = prop.getProperty("sbt.version")
    assert(res != null)
    res
  }

  /* api is for the communication between sbt <=> server <=> frontend */
  lazy val api = projectMatrix
    .in(file("api"))
    .settings(apiSettings)
    .jvmPlatform(ScalaVersions.cross)
    .jsPlatform(List(ScalaVersions.js), baseJsSettings)
    .enablePlugins(BuildInfoPlugin)

  lazy val sbtApiProject: Project = Project(id = "api-sbt", base = file("api-sbt"))
    .settings(sourceDirectory := baseDirectory.value / ".." / ".." / "api" / "src")
    .settings(apiSettings)
    .settings(scalaVersion := ScalaVersions.sbt)
    .enablePlugins(BuildInfoPlugin)

  private def apiSettings = {
    baseSettings ++ List(
      name := "api",
      libraryDependencies += {
        scalaVersion.value match {
          case v if v.startsWith("2.10") =>
            "com.typesafe.play" %%% "play-json" % "2.6.9"
          case v if v.startsWith("2.11") =>
            "com.typesafe.play" %%% "play-json" % "2.7.4"
          case _ =>
            "com.typesafe.play" %%% "play-json" % "2.9.0"
        }
      },
      buildInfoKeys := Seq[BuildInfoKey](
        organization,
        "runtimeProjectName" -> runtimeProjectName,
        "versionRuntime" -> versionRuntime,
        "latest210" -> ScalaVersions.latest210,
        "latest211" -> ScalaVersions.latest211,
        "latest212" -> ScalaVersions.latest212,
        "latest213" -> ScalaVersions.latest213,
        "stable3"   -> ScalaVersions.stable3,
        "latest3"   -> ScalaVersions.latest3,
        "jsScalaVersion" -> ScalaVersions.js,
        "defaultScalaJsVersion" -> ScalaJSVersions.current,
        "sbtVersion" -> readSbtVersion((ThisBuild / baseDirectory).value.toPath),
      ),
      buildInfoPackage := "com.olegych.scastie.buildinfo",
    )
  }

  /* runtime* pretty print values and type */
  lazy val `runtime-scala` = (projectMatrix in file(runtimeProjectName))
    .settings(
      baseSettings,
      version := versionRuntime,
      name := runtimeProjectName,
    )
    .jvmPlatform(ScalaVersions.cross)
    .jsPlatform(List(ScalaVersions.js), baseJsSettings)
    .dependsOn(api)

}
