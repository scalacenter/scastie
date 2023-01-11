import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbtbuildinfo.BuildInfoPlugin
import sbtbuildinfo.BuildInfoPlugin.autoImport._
import sbtprojectmatrix.ProjectMatrixPlugin.autoImport._

import java.io.FileInputStream
import java.nio.file._
import java.util.Properties

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
    val latest212 = "2.12.17"
    val latest213 = "2.13.10"
    val old3 = "3.0.2"
    val stable3 = "3.2.0"
    val latest3 = "3.2.1-RC2"
    val js = latest213
    val sbt = latest212
    val jvm = latest213
    val cross = List(latest210, latest211, latest212, latest213, old3, js, sbt, jvm).distinct
    val crossJS = List(latest212, latest213, js).distinct
  }

  object ScalaJSVersions {
    val current = "1.11.0"
  }

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

      if (scalaV == ScalaVersions.latest210 || scalaV.startsWith("3.")) base
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
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.2.0",
      "org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0" cross(CrossVersion.for3Use2_13),
    )
  )

  /* api is for the communication between sbt <=> server <=> frontend */
  lazy val api = projectMatrix
    .in(file("api"))
    .settings(apiSettings)
    .jvmPlatform(ScalaVersions.cross)
    .jsPlatform(ScalaVersions.crossJS, baseJsSettings)
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
            "com.typesafe.play" %%% "play-json" % "2.6.14"
          case v if v.startsWith("2.11") =>
            "com.typesafe.play" %%% "play-json" % "2.7.4"
          case _ =>
            "com.typesafe.play" %%% "play-json" % "2.10.0-RC5"
        }
      },
      semanticdbEnabled := { if (scalaVersion.value.startsWith("2.10")) false else semanticdbEnabled.value },
      buildInfoKeys := Seq[BuildInfoKey](
        organization,
        "runtimeProjectName" -> runtimeProjectName,
        "versionRuntime" -> versionRuntime,
        "latest210" -> ScalaVersions.latest210,
        "latest211" -> ScalaVersions.latest211,
        "latest212" -> ScalaVersions.latest212,
        "latest213" -> ScalaVersions.latest213,
        "stable3" -> ScalaVersions.stable3,
        "latest3" -> ScalaVersions.latest3,
        "jsScalaVersion" -> ScalaVersions.js,
        "defaultScalaJsVersion" -> ScalaJSVersions.current,
        "sbtVersion" -> sbtVersion.value,
      ),
      buildInfoPackage := "com.olegych.scastie.buildinfo",
    )
  }

  /* runtime* pretty print values and type */
  lazy val `runtime-scala` = (projectMatrix in file(runtimeProjectName))
    .jvmPlatform(ScalaVersions.cross)
    .jsPlatform(ScalaVersions.crossJS, baseJsSettings)
    .settings(
      baseSettings,
      version := versionRuntime,
      name := runtimeProjectName,
      semanticdbEnabled := { if (scalaVersion.value.startsWith("2.10")) false else semanticdbEnabled.value },
      inConfig(Compile)(
        unmanagedSourceDirectories ++= scala2MajorSourceDirs(scalaSource.value, virtualAxes.value),
      )
    )
    .dependsOn(api)

  /**
   * A sub project in projectMatrix, ex with scala 2.13.x,
   * is already configured with unmanagedSourceDirectories:
   *  src/main/{java, scala, scala-2, scala-2.13, scalajvm, scalajvm-2.13}
   * We need add src/main/scala{jvm|js}-2 for sources shared in all scala 2 sub projects in jvm|js platform.
   * @return Additional source directory names for scala 2.
   * @note scalajvm-3 is already configured by sbt-projectmatrix for scala 3 sub projects
   */
  private def scala2MajorSourceDirs(scalaSource: File, axisValues: Seq[VirtualAxis]): Seq[File] = {
    val platform = (axisValues collect {
      case pv: VirtualAxis.PlatformAxis => pv.directorySuffix
    }).head

    val svMajors = (axisValues collect {
      case sv: VirtualAxis.ScalaVersionAxis => sv.value.head
    }).filter(_ == '2')

    svMajors.map(v => scalaSource.getParentFile / s"scala$platform-$v")
  }
}
