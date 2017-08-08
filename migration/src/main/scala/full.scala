/*
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.6.2",
  "com.lihaoyi" %% "upickle" % "0.4.4"
)
 */

package com.olegych.scastie.api

import java.nio.file._
import System.{lineSeparator => nl}
import play.api.libs.json._

object Helper {
  def dontSerialize[T](fallback: T): Format[T] = new Format[T] {
    def writes(v: T): JsValue = JsNull
    def reads(json: JsValue): JsResult[T] = JsSuccess(fallback)
  }
}

import Helper.dontSerialize

sealed trait ScalaTarget
object ScalaTarget {
  implicit object ScalaTargetFormat extends Format[ScalaTarget] {
    private val formatJvm = Json.format[Jvm]
    private val formatJs = Json.format[Js]
    private val formatTypelevel = Json.format[Typelevel]
    private val formatNative = Json.format[Native]
    private val formatDotty = Json.format[Dotty]

    def writes(target: ScalaTarget): JsValue = {
      target match {
        case jvm: Jvm => {
          formatJvm.writes(jvm).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("Jvm")))
        }
        case js: Js => {
          formatJs.writes(js).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("Js")))
        }
        case typelevel: Typelevel => {
          formatTypelevel.writes(typelevel).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("Typelevel")))
        }
        case native: Native => {
          formatNative.writes(native).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("Native")))
        }
        case dotty: Dotty => {
          formatDotty.writes(dotty).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("Dotty")))
        }
      }
    }

    def reads(json: JsValue): JsResult[ScalaTarget] = {
      json match {
        case obj: JsObject => {
          val vs = obj.value

          vs.get("$type") match {
            case Some(JsString(tpe)) => {
              tpe match {
                case "Jvm"       => formatJvm.reads(json)
                case "Js"        => formatJs.reads(json)
                case "Typelevel" => formatTypelevel.reads(json)
                case "Native"    => formatNative.reads(json)
                case "Dotty"     => formatDotty.reads(json)
                case _           => JsError(Seq())
              }
            }
            case _ => JsError(Seq())
          }
        }
        case _ => JsError(Seq())
      }
    }
  }
  case class Jvm(scalaVersion: String) extends ScalaTarget
  case class Typelevel(scalaVersion: String) extends ScalaTarget
  case class Js(scalaVersion: String, scalaJsVersion: String)
      extends ScalaTarget
  case class Native(scalaVersion: String, scalaNativeVersion: String)
      extends ScalaTarget
  case class Dotty(dottyVersion: String = "0.2.0-RC1") extends ScalaTarget
}

object ScalaDependency {
  implicit val formatScalaDependency = Json.format[ScalaDependency]
}

case class ScalaDependency(
    groupId: String,
    artifact: String,
    target: ScalaTarget,
    version: String
)

object Project {
  implicit val formatProject = Json.format[Project]
}

case class Project(
    organization: String,
    repository: String,
    logo: Option[String] = None,
    artifacts: List[String] = Nil
)

object SnippetUserPart {
  implicit val formatSnippetUserPart = Json.format[SnippetUserPart]
}

case class SnippetUserPart(
    login: String,
    update: Option[Int]
)

object SnippetId {
  implicit val formatSnippetId = Json.format[SnippetId]
}

case class SnippetId(
    base64UUID: String,
    user: Option[SnippetUserPart]
)

object Inputs {
  type Hack = Map[ScalaDependency, Project]

  implicit val dontSerializeHack: Format[Hack] =
    dontSerialize[Hack](Map())

  implicit val formatInputs = Json.format[Inputs]
}

case class Inputs(
    worksheetMode: Boolean = false,
    code: String,
    target: ScalaTarget,
    libraries: Set[ScalaDependency],
    librariesFromList: List[(ScalaDependency, Project)] = List(),
    librariesFrom: Inputs.Hack = Map(),
    sbtConfigExtra: String,
    sbtPluginsConfigExtra: String,
    showInUserProfile: Boolean = false,
    forked: Option[SnippetId] = None
)

// outputs

sealed trait ConsoleOutput
object ConsoleOutput {
  implicit object ConsoleOutputFormat extends Format[ConsoleOutput] {
    val formatSbtOutput = Json.format[SbtOutput]
    private val formatUserOutput = Json.format[UserOutput]
    private val formatScastieOutput = Json.format[ScastieOutput]

    def writes(output: ConsoleOutput): JsValue = {
      output match {
        case sbtOutput: SbtOutput => {
          formatSbtOutput.writes(sbtOutput).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("SbtOutput")))
        }

        case userOutput: UserOutput => {
          formatUserOutput.writes(userOutput).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("UserOutput")))
        }

        case scastieOutput: ScastieOutput => {
          formatScastieOutput.writes(scastieOutput).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("ScastieOutput")))
        }
      }
    }

    def reads(json: JsValue): JsResult[ConsoleOutput] = {
      json match {
        case obj: JsObject => {
          val vs = obj.value

          vs.get("$type") match {
            case Some(tpe) => {
              tpe match {
                case JsString("SbtOutput")  => formatSbtOutput.reads(json)
                case JsString("UserOutput") => formatUserOutput.reads(json)
                case JsString("ScastieOutput") =>
                  formatScastieOutput.reads(json)
                case _ => JsError(Seq())
              }
            }
            case None => JsError(Seq())
          }
        }
        case _ => JsError(Seq())
      }
    }
  }

  case class SbtOutput(line: String) extends ConsoleOutput
  case class UserOutput(line: String) extends ConsoleOutput
  case class ScastieOutput(line: String) extends ConsoleOutput
}

object Severity {
  implicit object SeverityFormat extends Format[Severity] {
    def writes(severity: Severity): JsValue =
      severity match {
        case Info    => JsString("Info")
        case Warning => JsString("Warning")
        case Error   => JsString("Error")
      }

    def reads(json: JsValue): JsResult[Severity] = {
      json match {
        case JsString("Info")    => JsSuccess(Info)
        case JsString("Warning") => JsSuccess(Warning)
        case JsString("Error")   => JsSuccess(Error)
        case _                   => JsError(Seq())
      }
    }
  }
}

sealed trait Severity
case object Info extends Severity
case object Warning extends Severity
case object Error extends Severity

object Problem {
  implicit val formatProblem = Json.format[Problem]
}

case class Problem(
    severity: Severity,
    line: Option[Int],
    message: String
)

object Render {
  implicit object RenderFormat extends Format[Render] {
    private val formatValue = Json.format[Value]
    private val formatHtml = Json.format[Html]
    private val formatAttachedDom = Json.format[AttachedDom]

    def writes(render: Render): JsValue = {

      render match {
        case v: Value => {
          formatValue.writes(v).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("Value")))
        }

        case h: Html => {
          formatHtml.writes(h).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("Html")))
        }

        case a: AttachedDom => {
          formatAttachedDom.writes(a).asInstanceOf[JsObject] ++
            JsObject(Seq("$type" -> JsString("AttachedDom")))
        }
      }
    }

    def reads(json: JsValue): JsResult[Render] = {
      json match {
        case obj: JsObject => {
          val vs = obj.value

          vs.get("$type") match {
            case Some(tpe) => {
              tpe match {
                case JsString("Value")       => formatValue.reads(json)
                case JsString("Html")        => formatHtml.reads(json)
                case JsString("AttachedDom") => formatAttachedDom.reads(json)
                case _                       => JsError(Seq())
              }
            }
            case None => JsError(Seq())
          }
        }
        case _ => JsError(Seq())
      }
    }
  }
}

sealed trait Render
case class Value(v: String, className: String) extends Render
case class Html(a: String, folded: Boolean = false) extends Render
case class AttachedDom(uuid: String, folded: Boolean = false) extends Render

object Position {
  implicit val formatPosition = Json.format[Position]
}

case class Position(start: Int, end: Int)

object Instrumentation {
  implicit val formatInstrumentation = Json.format[Instrumentation]
}

case class Instrumentation(
    position: Position,
    render: Render
)

object RuntimeError {
  implicit val formatRuntimeError = Json.format[RuntimeError]
}

case class RuntimeError(
    message: String,
    line: Option[Int],
    fullStack: String
)

object SnippetProgress {
  implicit val formatSnippetProgress = Json.format[SnippetProgress]
}

case class SnippetProgress(
    snippetId: Option[SnippetId],
    userOutput: Option[String],
    sbtOutput: Option[String],
    compilationInfos: List[Problem],
    instrumentations: List[Instrumentation],
    runtimeError: Option[RuntimeError],
    scalaJsContent: Option[String] = None,
    scalaJsSourceMapContent: Option[String] = None,
    done: Boolean,
    timeout: Boolean,
    sbtError: Boolean = false,
    forcedProgramMode: Boolean
)

import java.nio.file._
import scala.collection.JavaConverters._

object Main {

  def main(args: Array[String]): Unit = {
    import upickle.default._

    val dir = Paths.get(args.head)
    assert(Files.isDirectory(dir))

    val inputs = collection.mutable.Buffer.empty[Path]
    val outputs = collection.mutable.Buffer.empty[Path]

    val ds = Files.newDirectoryStream(dir)
    ds.asScala.foreach { user =>
      println(user)
      if (!(user.getFileName.toString == ".snapshot")) {
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
    }
    ds.close()

    println()
    println("Scanning done. Found")
    println("  " + inputs.size + " inputs")
    println("  " + outputs.size + " outputs")

    var failedInputs = 0
    var successInputs = 0

    inputs.foreach { path =>
      val content = Files.readAllLines(path).toArray.mkString(nl)
      try {
        val inputs0 = read[Inputs](content)
        val inputs =
          inputs0.copy(
            librariesFromList = inputs0.librariesFrom.toList
          )

        val json = Json.prettyPrint(Json.toJson(inputs))
        val path2 = path.getParent().resolve("input2.json")
        Files.write(path2, json.getBytes)

        successInputs += 1
        if (successInputs % 100 == 0) {
          print("*")
        }
      } catch {
        case scala.util.control.NonFatal(e) => {
          failedInputs += 1
        }
      }
    }

    println("Inputs")
    println("  success: " + successInputs)
    println("  failure: " + failedInputs)

    var failedOutputs = 0
    var successOutputs = 0

    outputs.foreach { path =>
      try {
        val outputsLines0 =
          Files.readAllLines(path).toArray.toList.map { line =>
            read[SnippetProgress](line.toString)
          }

        val outputsLines =
          outputsLines0
            .map(ouputs => Json.stringify(Json.toJson(ouputs)))
            .mkString(nl)

        val path2 = path.getParent().resolve("output2.json")

        Files.write(path2, outputsLines.getBytes)

        successOutputs += 1

        if (successOutputs % 100 == 0) {
          print("*")
        }
      } catch {
        case scala.util.control.NonFatal(e) => {
          failedOutputs += 1
        }
      }
    }

    println("Outputs")
    println("  success: " + successOutputs)
    println("  failure: " + failedOutputs)
  }
}
