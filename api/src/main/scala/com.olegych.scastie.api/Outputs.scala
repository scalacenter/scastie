package com.olegych.scastie
package api

case class ReleaseOptions(groupId: String, versions: List[String])

// case class MavenReference(groupId: String, artifactId: String, version: String)

// outputs
object Outputs {
  def default = Outputs(
    consoleOutputs = Vector(),
    compilationInfos = Set(),
    instrumentations = Set(),
    runtimeError = None
  )
}
case class Outputs(
  consoleOutputs: Vector[(String, ConsoleOutputType)],
  compilationInfos: Set[Problem],
  instrumentations: Set[Instrumentation],
  runtimeError: Option[RuntimeError]
) {

  def console: String = consoleOutputs.map{ case (line, outputType) =>
    outputType.prompt + line
  }.mkString("")

  def isClearable: Boolean =
    !consoleOutputs.isEmpty ||
      !compilationInfos.isEmpty ||
      !instrumentations.isEmpty ||
      !runtimeError.isEmpty
}

case class Position(start: Int, end: Int)

case class CompilationInfo(
  severity: Severity,
  position: Position,
  message: String
)


sealed trait ConsoleOutputType { 
  def prompt: String
}
object ConsoleOutputType {
  case object SbtOutput extends ConsoleOutputType { 
    def prompt: String = "sbt: "
  }

  case object UserOutput extends ConsoleOutputType { 
    def prompt: String = "user: "
  }

  case object ScastieOutput extends ConsoleOutputType {
    def prompt: String = "scastie: "
  }

  import upickle.default._

  implicit val pkl: ReadWriter[ConsoleOutputType] =
    macroRW[SbtOutput.type] merge 
    macroRW[UserOutput.type] merge 
    macroRW[ScastieOutput.type]
}

