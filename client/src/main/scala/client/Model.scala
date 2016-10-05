package client

// input
case class Inputs(
  code: String = "",
  libraries: Set[Library] = Set()
)
case class Library(groupId: String, artifactId: String, version: String)

// outputs
case class Outputs(
  console: Vector[String] = Vector(),
  compilationInfos: Set[api.Problem] = Set(),
  instrumentations: Set[api.Instrumentation] = Set()
)

sealed trait Severity
final case object Info extends Severity
final case object Warning extends Severity
final case object Error extends Severity

case class Position(start: Int, end: Int)

case class CompilationInfo(
  severity: Severity,
  position: Position,
  message: String
)