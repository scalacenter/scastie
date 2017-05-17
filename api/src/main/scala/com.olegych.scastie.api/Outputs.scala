package com.olegych.scastie
package api

case class ReleaseOptions(groupId: String,
                          versions: List[String],
                          version: String)

// case class MavenReference(groupId: String, artifactId: String, version: String)

// outputs
object Outputs {
  def default = Outputs(
    consoleOutputs = Vector(),
    compilationInfos = Set(),
    instrumentations = Set(),
    runtimeError = None,
    sbtError = false
  )
}
case class Outputs(
    consoleOutputs: Vector[ConsoleOutput],
    compilationInfos: Set[Problem],
    instrumentations: Set[Instrumentation],
    runtimeError: Option[RuntimeError],
    sbtError: Boolean
) {

  def console: String = consoleOutputs.map(_.show).mkString("\n")

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

sealed trait ConsoleOutput {
  def show: String
}
object ConsoleOutput {
  case class SbtOutput(line: String) extends ConsoleOutput {
    def show: String = s"sbt: $line"
  }

  case class UserOutput(line: String) extends ConsoleOutput {
    def show: String = line
  }

  case class ScastieOutput(line: String) extends ConsoleOutput {
    def show: String = s"scastie: $line"
  }
}
