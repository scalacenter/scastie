import java.io.FileInputStream
import java.nio.file._
import java.util.Properties

import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt._
import sbt.Keys._
import sbtbuildinfo.BuildInfoPlugin
import sbtbuildinfo.BuildInfoPlugin.autoImport._
import sbtprojectmatrix.ProjectMatrixPlugin.autoImport._

/*
This code is shared between the build and the "build-build".
it allows us to use the api project at the build level
to generate the docker image for the different
configuration matrix
 */
object SbtShared {

  object ScalaVersions {
    val latest210  = "2.10.7"
    val latest211  = "2.11.12"
    val latest212  = "2.12.20"
    val latest213  = "2.13.16"
    val old3       = "3.0.2"
    val stableLTS  = "3.3.6"
    val stableNext = "3.7.2"
    val latestLTS  = "3.3.6"
    val latestNext = "3.7.3-RC1"
    val js         = latest213
    val sbt        = latest212
    val jvm        = latest213
    val cross      =
      List(latest210, latest211, latest212, latest213, old3, stableLTS, stableNext, js, sbt, jvm).distinct
    val crossJS    = List(latest212, latest213, js).distinct
  }

  object ScalaJSVersions {
    val current = "1.19.0"
  }

  val scalaCliVersion = "1.0.4"
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

  val gitHashNow    = gitHash()
  val gitIsDirtyNow = gitIsDirty()

  val versionNow = {
    val base = "1.0.0"
    if (gitIsDirtyNow) base + "-SNAPSHOT"
    else {
      val hash = gitHashNow
      s"$base+$hash"
    }
  }

  lazy val versionRuntime = "1.0.0-SNAPSHOT"

  lazy val orgSettings = Seq(
    organization := "org.scastie",
    version      := versionNow
  )

  lazy val baseSettings = Seq(
    // skip scaladoc
    Compile / packageDoc / publishArtifact := false,
    packageSrc / publishArtifact           := false,
    Compile / doc / sources                := Seq.empty,
    Test / parallelExecution               := false,
    scalacOptions ++= {
      val scalaV = scalaVersion.value

      val base = Seq(
        "-deprecation",
        "-encoding",
        "UTF-8",
        "-feature",
        "-unchecked"
      )

      if (scalaV == ScalaVersions.latest210 || scalaV.startsWith("3.")) base
      else {
        base ++ Seq(
          "-Yrangepos"
        )
      }

    },
    console := (Test / console).value,
    Test / console / scalacOptions -= "-Ywarn-unused-import",
    Compile / consoleQuick / scalacOptions -= "-Ywarn-unused-import"
  ) ++ orgSettings

  lazy val baseNoCrossSettings = baseSettings ++ Seq(
    scalaVersion := ScalaVersions.jvm
  )

  lazy val baseJsSettings = Seq(
    test := {},
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom"               % "2.8.0",
      "org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0" cross (CrossVersion.for3Use2_13)
    )
  )

  /* api is for the communication between sbt <=> server <=> frontend */
  lazy val api = projectMatrix
    .in(file("api"))
    .settings(apiSettings)
    .jvmPlatform(Seq(ScalaVersions.jvm, ScalaVersions.stableLTS, ScalaVersions.stableNext, ScalaVersions.sbt))
    .jsPlatform(Seq(ScalaVersions.js), baseJsSettings)
    .enablePlugins(BuildInfoPlugin)

  lazy val `runtime-api` = projectMatrix
    .in(file("runtime-api"))
    .settings(
      semanticdbEnabled := { if (scalaVersion.value.startsWith("2.10")) false else semanticdbEnabled.value },
    )
    .jvmPlatform(ScalaVersions.cross)
    .jsPlatform(ScalaVersions.crossJS, baseJsSettings)

  private def apiSettings = {
    baseSettings ++ List(
      name := "api",
      libraryDependencies ++= {
        scalaVersion.value match {
          case v if v.startsWith("2.10") => Seq("io.circe" %% "circe-core" % "0.9.3", "io.circe" %%% "circe-generic" % "0.9.3")
          case v if v.startsWith("2.11") => Seq("io.circe" %% "circe-core" % "0.11.2", "io.circe" %%% "circe-generic" % "0.11.2")
          case _                         => Seq("io.circe" %% "circe-core" % "0.14.6", "io.circe" %%% "circe-generic" % "0.14.6")
        }
      },
      Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "runtime-api",
      semanticdbEnabled := { if (scalaVersion.value.startsWith("2.10")) false else semanticdbEnabled.value },
      buildInfoKeys := Seq[BuildInfoKey](
        organization,
        "runtimeProjectName"    -> runtimeProjectName,
        "versionRuntime"        -> versionRuntime,
        "latest210"             -> ScalaVersions.latest210,
        "latest211"             -> ScalaVersions.latest211,
        "latest212"             -> ScalaVersions.latest212,
        "latest213"             -> ScalaVersions.latest213,
        "stableLTS"             -> ScalaVersions.stableLTS,
        "stableNext"            -> ScalaVersions.stableNext,
        "latestLTS"             -> ScalaVersions.latestLTS,
        "latestNext"            -> ScalaVersions.latestNext,
        "jsScalaVersion"        -> ScalaVersions.js,
        "defaultScalaJsVersion" -> ScalaJSVersions.current,
        "sbtVersion"            -> sbtVersion.value,
        "scalaCliVersion"       -> scalaCliVersion
      ),
      buildInfoPackage := "org.scastie.buildinfo"
    )
  }

  /* runtime* pretty print values and type */
  lazy val `runtime-scala` = (projectMatrix in file(runtimeProjectName))
    .jvmPlatform(ScalaVersions.cross)
    .jsPlatform(ScalaVersions.crossJS, baseJsSettings)
    .settings(
      baseSettings,
      version           := versionRuntime,
      name              := runtimeProjectName,
      Compile / unmanagedSourceDirectories += (ThisBuild / baseDirectory).value / "runtime-api",
      semanticdbEnabled := { if (scalaVersion.value.startsWith("2.10")) false else semanticdbEnabled.value },
      libraryDependencies ++= {
        scalaVersion.value match {
          case v if v.startsWith("2") => Seq("org.scala-lang" % "scala-reflect" % v)
          case _                      => Seq()
        }
      },
      inConfig(Compile)(
        unmanagedSourceDirectories ++= scala2MajorSourceDirs(scalaSource.value, virtualAxes.value)
      )
    )

  /**
    * A sub project in projectMatrix, ex with scala 2.13.x, is already configured with unmanagedSourceDirectories:
    * src/main/{java, scala, scala-2, scala-2.13, scalajvm, scalajvm-2.13} We need add src/main/scala{jvm|js}-2 for
    * sources shared in all scala 2 sub projects in jvm|js platform.
    * @return
    *   Additional source directory names for scala 2.
    * @note
    *   scalajvm-3 is already configured by sbt-projectmatrix for scala 3 sub projects
    */
  private def scala2MajorSourceDirs(scalaSource: File, axisValues: Seq[VirtualAxis]): Seq[File] = {
    val platform = (axisValues collect { case pv: VirtualAxis.PlatformAxis => pv.directorySuffix }).head

    val svMajors = (axisValues collect { case sv: VirtualAxis.ScalaVersionAxis => sv.value.head }).filter(_ == '2')

    svMajors.map(v => scalaSource.getParentFile / s"scala$platform-$v")
  }

}
