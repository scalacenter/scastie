import com.olegych.scastie.api._

import java.nio.file._
import System.{lineSeparator => nl}
import play.api.libs.json._
import play.api.libs.functional.syntax._

import java.nio.file._
import scala.collection.JavaConverters._

import play.api.libs.json._
import play.api.libs.json.OFormat

object SnippetProgressV1 {
  implicit val formatSnippetProgressV1: Reads[SnippetProgressV1] =
    Json.reads[SnippetProgressV1]
}

case class SnippetProgressV1(
    snippetId: Option[SnippetIdV1],
    userOutput: Option[String],
    sbtOutput: Option[String],
    compilationInfos: List[Problem],
    instrumentations: List[Instrumentation],
    runtimeError: Option[RuntimeError],
    scalaJsContent: Option[String],
    scalaJsSourceMapContent: Option[String],
    done: Boolean,
    timeout: Boolean,
    sbtError: Boolean,
    forcedProgramMode: Boolean
)

object InputsV1 {
  implicit val reads: Reads[InputsV1] =
    Json.reads[InputsV1]
}

case class InputsV1(
    worksheetMode: Boolean,
    code: String,
    target: ScalaTarget,
    libraries: Set[ScalaDependency],
    librariesFromList: List[(ScalaDependency, ProjectV1)],
    sbtConfigExtra: String,
    sbtPluginsConfigExtra: String,
    showInUserProfile: Boolean = false,
    forked: Option[SnippetIdV1]
)

object ProjectV1 {
  implicit val reads: Reads[ProjectV1] = {
    (
      (JsPath \ "organization").read[String] and
        (JsPath \ "repository").read[String] and
        (
          (JsPath \ "logo").read[List[String]].map(logo => logo.headOption) or
            (JsPath \ "logo").readNullable[String]
        ) and
        (JsPath \ "artifacts").read[List[String]]
    )(ProjectV1.apply _)
  }
}

case class ProjectV1(
    organization: String,
    repository: String,
    logo: Option[String],
    artifacts: List[String]
)

object UserV1 {
  implicit val formatUser: OFormat[UserV1] =
    Json.format[UserV1]
}
case class UserV1(login: String, name: Option[String], avatar_url: String)

object SnippetUserPartV1 {
  implicit val reads: Reads[SnippetUserPartV1] = {
    val r1 =
      (
        (JsPath \ "login").read[String] and
          (JsPath \ "update").read[Int]
      )(SnippetUserPartV1.apply _)

    val r2 =
      (JsPath \ "login").read[String].map(login => SnippetUserPartV1(login, 0))

    r1 or r2
  }
}

case class SnippetUserPartV1(login: String, update: Int)

object SnippetIdV1 {
  implicit val reads: Reads[SnippetIdV1] =
    Json.reads[SnippetIdV1]
}

case class SnippetIdV1(base64UUID: String, user: Option[SnippetUserPartV1])

object Main {
  def main(args: Array[String]): Unit = {
    val dir = Paths.get(args.head)
    assert(Files.isDirectory(dir))

    val inputs = collection.mutable.Buffer.empty[Path]
    val outputs = collection.mutable.Buffer.empty[Path]

    /*

    _anonymous_
      MSTWUrJdQ6uIfeIRTr5n4g
        input2.json
        output2.json

    ches
      project_7xo3oHKVRgmQRxZYUxAnRA/
      project_7xo3oHKVRgmQRxZYUxAnRA.zip

      7xo3oHKVRgmQRxZYUxAnRA
        0
          input2.json

    .snapshot (epfl backup)

     */

    def addSnippet(dir: Path): Unit = {
      val in = dir.resolve("input2.json")
      if (Files.isRegularFile(in)) {
        inputs += in
      }
      val out = dir.resolve("output2.json")
      if (Files.isRegularFile(out)) {
        outputs += out
      }
    }

    val ignored = Set(".snapshot", ".gitignore", "_anonymous_")

    val ds = Files.newDirectoryStream(dir)
    ds.asScala.foreach { user =>
      println(user)

      if (!(ignored.contains(user.getFileName.toString))) {
        val userStream = Files.newDirectoryStream(user)
        userStream.asScala.foreach { base64UUID =>
          if (Files.isDirectory(base64UUID) &&
              !base64UUID.getFileName.toString.startsWith("project_")) {

            val baseStream = Files.newDirectoryStream(base64UUID)
            baseStream.asScala.foreach { update =>
              addSnippet(update)
            }
            baseStream.close

            addSnippet(base64UUID)
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

    val failedInputs = collection.mutable.Buffer.empty[(Path, Throwable)]
    var successInputs = 0

    def doSuccessInput(inputs: Inputs, path: Path): Unit = {
      successInputs += 1
      if (successInputs % 100 == 0) {
        print("*")
      }

      val json = Json.prettyPrint(Json.toJson(inputs))
      val path2 = path.getParent().resolve("input3.json")
      Files.write(path2, json.getBytes)
    }

    inputs.foreach { path =>
      val content = Files.readAllLines(path).toArray.mkString(nl)
      try {
        Json.fromJson[Inputs](Json.parse(content)) match {
          case JsSuccess(inputs, _) => {
            doSuccessInput(inputs, path)
          }
          case jsError1: JsError => {
            Json.fromJson[InputsV1](Json.parse(content)) match {
              case JsSuccess(inputs, _) => {
                doSuccessInput(convert(inputs), path)
              }
              case jsError2: JsError => {
                throw new Exception(
                  content + nl + nl +
                    jsError1.toString + nl + nl +
                    jsError2.toString
                )
              }
            }
          }
        }
      } catch {
        case scala.util.control.NonFatal(e) => {
          failedInputs += ((path, e))
        }
      }
    }

    println("Inputs")
    println("  success: " + successInputs)
    println("  failure: " + failedInputs.size)

    failedInputs
      .take(3)
      .map(
        inputs =>
          nl +
            "-----------------" +
            nl +
            inputs.toString +
            nl +
            "-----------------" +
          nl
      )
      .foreach(println)

    val failedOutputs = collection.mutable.Buffer.empty[(Path, Throwable)]
    var successOutputs = 0

    outputs.foreach { path =>
      try {
        val outputsLines0 =
          Files
            .readAllLines(path)
            .asScala
            .map(
              line =>
                Json.fromJson[SnippetProgressV1](Json.parse(line)) match {
                  case JsSuccess(v, _) => v
                  case jsError: JsError => {
                    throw new Exception(
                      jsError.toString + nl +
                        line
                    )
                  }
              }
            )

        val outputsLines =
          outputsLines0
            .map(ouputs => Json.stringify(Json.toJson(convert(ouputs))))
            .mkString(nl)

        val path2 = path.getParent().resolve("output3.json")

        Files.write(path2, outputsLines.getBytes)

        successOutputs += 1

        if (successOutputs % 100 == 0) {
          print("*")
        }
      } catch {
        case scala.util.control.NonFatal(e) => {
          failedOutputs += ((path, e))
        }
      }
    }

    println("Outputs")
    println("  success: " + successOutputs)
    println("  failure: " + failedOutputs.size)

    failedOutputs
      .take(3)
      .map(
        inputs =>
          nl +
            "-----------------" +
            nl +
            inputs.toString +
            nl +
            "-----------------" +
          nl
      )
      .foreach(println)
  }

  private def convert(p: ProjectV1): Project = {
    import p._

    Project(
      organization,
      repository,
      logo,
      artifacts
    )
  }

  private def convert(inputs: InputsV1): Inputs = {
    import inputs._

    Inputs(
      isWorksheetMode = worksheetMode,
      code = code,
      target = target,
      libraries = libraries,
      librariesFromList = librariesFromList.map {
        case (sd, p) => (sd, convert(p))
      },
      sbtConfigExtra = sbtConfigExtra,
      sbtPluginsConfigExtra = sbtPluginsConfigExtra,
      isShowingInUserProfile = showInUserProfile,
      forked = forked.map(convert),
    )
  }

  private def convert(user: SnippetUserPartV1): SnippetUserPart = {
    import user._
    SnippetUserPart(login, update)
  }

  private def convert(snippetId: SnippetIdV1): SnippetId = {
    import snippetId._

    SnippetId(
      base64UUID = base64UUID,
      user = user.map(convert)
    )
  }

  private def convert(progress: SnippetProgressV1): SnippetProgress = {
    import progress._

    def stdout(line: String) =
      ProcessOutput(line, ProcessOutputType.StdOut)

    SnippetProgress(
      snippetId = snippetId.map(convert),
      userOutput = userOutput.map(stdout),
      sbtOutput = sbtOutput.map(stdout),
      compilationInfos = compilationInfos,
      instrumentations = instrumentations,
      runtimeError = runtimeError,
      scalaJsContent = scalaJsContent,
      scalaJsSourceMapContent = scalaJsSourceMapContent,
      isDone = done,
      isTimeout = timeout,
      isSbtError = sbtError,
      isForcedProgramMode = forcedProgramMode,
    )
  }
}
