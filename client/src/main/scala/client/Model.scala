package client

case class Project(
    organization: String,
    repository: String,
    logo: Option[String] = None,
    artifacts: List[String] = Nil
)

case class ReleaseOptions(
    groupId: String,
    artifacts: List[String],
    versions: List[String]
)

// case class MavenReference(groupId: String, artifactId: String, version: String)

// outputs
object Outputs {
    def default = Outputs(
      console = Vector(),
      compilationInfos = Set(),
      instrumentations = Set()
    )
}
case class Outputs(
    console: Vector[String],
    compilationInfos: Set[api.Problem],
    instrumentations: Set[api.Instrumentation]
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
