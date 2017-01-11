package client

case class Project(
    organization: String,
    repository: String,
    logo: Option[String] = None,
    artifacts: List[String] = Nil
)

case class ReleaseOptions(groupId: String,
                          artifacts: List[String],
                          versions: List[String])

// case class MavenReference(groupId: String, artifactId: String, version: String)

// input


// outputs
case class Outputs(
    console: Vector[String] = Vector(),
    compilationInfos: Set[api.Problem] = Set(),
    instrumentations: Set[api.Instrumentation] = Set()
)

sealed trait Severity
final case object Info    extends Severity
final case object Warning extends Severity
final case object Error   extends Severity

case class Position(start: Int, end: Int)

case class CompilationInfo(
    severity: Severity,
    position: Position,
    message: String
)
