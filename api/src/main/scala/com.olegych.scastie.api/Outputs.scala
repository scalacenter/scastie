package com.olegych.scastie
package api

case class ReleaseOptions(groupId: String, versions: List[String])

// case class MavenReference(groupId: String, artifactId: String, version: String)

// outputs
object Outputs {
  def default = Outputs(
    console = Vector(),
    compilationInfos = Set(),
    instrumentations = Set(),
    runtimeError = None
  )

  import upickle.default._
  implicit val pkl: ReadWriter[Outputs] = macroRW[Outputs]
}
case class Outputs(
  console: Vector[String],
  compilationInfos: Set[Problem],
  instrumentations: Set[Instrumentation],
  runtimeError: Option[RuntimeError]
) {
  def isClearable: Boolean =
    !console.isEmpty ||
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

case class SbtOutput(line: String)
