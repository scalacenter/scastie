import sbt._
import java.io._

import java.nio.file._

import com.olegych.scastie.api._
import com.olegych.scastie.buildinfo.BuildInfo.sbtVersion

import System.{lineSeparator => nl}

import SbtShared._

class GenerateProjects(sbtTargetDir: Path) {
  val projectTarget: Path = sbtTargetDir.resolve("projects")

  val projects: List[GeneratedProject] = {
    val helloWorld =
      """|object Main {
         |  def main(args: Array[String]): Unit = {
         |    println("Hello, World!")
         |  }
         |}
         |""".stripMargin

    val default =
      Inputs.default.copy(
        code = helloWorld,
        _isWorksheetMode = false
      )

    def scala(version: String): Inputs = {
      val playJson = version match {
        case v if v.startsWith("2.13") =>
          s"""libraryDependencies += "com.typesafe.play" % "play-json_2.13.0-RC2" % "$playJsonVersion213" """
        case _ =>
          s"""libraryDependencies += "com.typesafe.play" %% "play-json" % "$playJsonVersion" """
      }
      default.copy(
        target = ScalaTarget.Jvm(version),
        sbtConfigExtra = default.sbtConfigExtra + nl + nl + playJson
      )
    }

    val scala210 = scala(sbt210)
    val scala211 = scala(latest211)
    val scala212 = scala(latest212)
    val scala213 = scala(latest213)

    val dotty =
      default.copy(
        target = ScalaTarget.Dotty.default
      )

    val typelevel =
      default.copy(
        target = ScalaTarget.Typelevel.default
      )

    val scalaJs =
      default.copy(
        target = ScalaTarget.Js.default,
        sbtConfigExtra =
          default.sbtConfigExtra + nl + nl +
            s"""libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "$scalajsDomVersion""""
      )

    List(
      (scala210, "scala210"),
      (scala211, "scala211"),
      (scala212, "scala212"),
      (scala213, "scala213"),
      (dotty, "dotty"),
      (typelevel, "typelevel"),
      (scalaJs, "scalaJs")
    ).map {
      case (inputs, name) =>
        new GeneratedProject(
          inputs,
          projectTarget.resolve(name)
        )
    }
  }

  def generateSbtProjects(): Unit =
    projects.foreach(_.generateSbtProject)
}

class GeneratedProject(inputs: Inputs, sbtDir: Path) {
  private val buildFile = sbtDir.resolve("build.sbt")
  private val projectDir = sbtDir.resolve("project")
  private val pluginFile = projectDir.resolve("plugins.sbt")
  private val codeFile = sbtDir.resolve("src/main/scala/main.scala")

  def generateSbtProject(): Unit = {
    Files.createDirectories(projectDir)

    Files.write(
      projectDir.resolve("build.properties"),
      s"sbt.version = $sbtVersion".getBytes
    )

    Files.write(buildFile, inputs.sbtConfig.getBytes)
    Files.write(pluginFile, inputs.sbtPluginsConfig.getBytes)

    Files.createDirectories(codeFile.getParent)
    Files.write(codeFile, inputs.code.getBytes)
  }

  def runCmd(dest: String): String = {
    val dir = sbtDir.getFileName

    s"""cd $dest/$dir && sbt "${inputs.target.sbtRunCommand}""""
  }
}
