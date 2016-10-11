package client

case class Project(
  
)

// case class JvmDependency(mavenReference: MavenReference)
// case class ScalaDependency()

case class MavenReference(groupId: String, artifactId: String, version: String)

sealed trait ScalaVersion

case class Dotty(version: Version)

case class Version(_1: Int, _2: Int, _3: Int, extra: String = "") {
  def binary: String = s"${_1}.${_2}" // like 2.11
  override def toString: String = s"${_1}.${_2}.${_3}$extra"
}

sealed trait ScalaTarget
object ScalaTarget {
  private val defaultScalaVersion = Version(2, 11, 8)
  private val defaultScalaJsVersion = Version(0, 6, 12)

  case class Jvm(scalaVersion: Version = defaultScalaVersion) extends ScalaTarget
  case class Js(scalaVersion: Version = defaultScalaVersion, 
                scalaJsVersion: Version = defaultScalaJsVersion) extends ScalaTarget
  // case object Native extends ScalaTarget
  case object Dotty extends ScalaTarget
}


// input
case class Inputs(
  code: String = "",
  target: ScalaTarget = ScalaTarget.Jvm(),
  libraries: Set[Library] = Set()
)
case class Library()

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