package com.olegych.scastie.api

import java.nio.file._
import System.{lineSeparator => nl}

case class Inputs(
    worksheetMode: Boolean = false,
    code: String,
    target: ScalaTarget,
    libraries: Set[ScalaDependency],
    librariesFromList: List[(ScalaDependency, Project)] = List(),
    sbtConfigExtra: String,
    sbtPluginsConfigExtra: String,
    showInUserProfile: Boolean = false,
    forked: Option[SnippetId] = None
)

sealed trait ScalaTarget
object ScalaTarget {
  case class Jvm(scalaVersion: String) extends ScalaTarget
  case class Typelevel(scalaVersion: String) extends ScalaTarget
  case class Js(scalaVersion: String, scalaJsVersion: String)
      extends ScalaTarget
  case class Native(scalaVersion: String, scalaNativeVersion: String)
      extends ScalaTarget
  case class Dotty(
      dottyVersion: String = "0.2.0-RC1"
  ) extends ScalaTarget

}

case class ScalaDependency(
    groupId: String,
    artifact: String,
    target: ScalaTarget,
    version: String
)

case class Project(
    organization: String,
    repository: String,
    logo: Option[List[String]] = None,
    artifacts: List[String] = Nil
)

case class SnippetId(
    base64UUID: String,
    user: Option[SnippetUserPart]
)

case class SnippetUserPart(
    login: String,
    update: Option[Int]
)

case class Outputs(
    consoleOutputs: Vector[ConsoleOutput] = Vector(),
    compilationInfos: Set[Problem],
    instrumentations: Set[Instrumentation],
    runtimeError: Option[RuntimeError],
    sbtError: Boolean = false
)

sealed trait ConsoleOutput
object ConsoleOutput {
  case class SbtOutput(line: String) extends ConsoleOutput
  case class UserOutput(line: String) extends ConsoleOutput
  case class ScastieOutput(line: String) extends ConsoleOutput
}

case class Problem(
    severity: Severity,
    line: Option[Int],
    message: String
)

sealed trait Severity
case object Info extends Severity
case object Warning extends Severity
case object Error extends Severity

sealed trait Render
case class Value(v: String, className: String) extends Render
case class Html(a: String, folded: Boolean = false) extends Render
case class AttachedDom(uuid: String, folded: Boolean = false) extends Render

case class Position(start: Int, end: Int)

case class Instrumentation(
    position: Position,
    render: Render
)

case class RuntimeError(
    message: String,
    line: Option[Int],
    fullStack: String
)

import java.nio.file._
import scala.collection.JavaConverters._

object Main {

  def main(args: Array[String]): Unit = {
    import upickle.default._

    // val dir = Paths.get("/home/gui/scastie/server/target/snippets")
    val dir = Paths.get("/home/gui/snippetsmis")
    Files.isDirectory(dir)

    val inputs = collection.mutable.Buffer.empty[Path]
    val outputs = collection.mutable.Buffer.empty[Path]

    val ds = Files.newDirectoryStream(dir)
    ds.asScala.foreach { user =>
      val userStream = Files.newDirectoryStream(user)
      userStream.asScala.foreach { base =>
        if (Files.isDirectory(base)) {
          if (!base.getFileName.toString.startsWith("project_")) {
            val baseStream = Files.newDirectoryStream(base)
            baseStream.asScala.foreach { sid =>
              val in = sid.resolve("input.json")
              if (Files.isRegularFile(in)) {
                inputs += in
              }
              val out = sid.resolve("output.json")
              if (Files.isRegularFile(out)) {
                outputs += out
              }
            }
            baseStream.close
          }
        }
      }
      userStream.close
    }
    ds.close()

    val inputReads =
      inputs.count { path =>
        val content = Files.readAllLines(path).toArray.mkString(nl)
        try {
          read[Inputs](content)
          true
        } catch {
          case util.control.NonFatal(e) => {
            false
          }
        }
      }

    println(inputReads)
    println(inputs.size)

    val outputReads =
      outputs.count { path =>
        try {
          Files.readAllLines(path).toArray.foreach { line =>
            read[Outputs](line.toString)
          }
          true
        } catch {
          case util.control.NonFatal(e) => {

            false
          }
        }
      }

    println(outputReads)
    println(outputs.size)
  }
}
