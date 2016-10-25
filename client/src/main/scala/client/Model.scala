package client

case class Project(
  organization: String,
  repository: String,
  logo: Option[String] = None,
  artifacts: List[String] = Nil
)

case class ReleaseOptions(
  artifacts: List[String],
  versions: List[String],
  
  groupId: String,
  artifactId: String,
  version: String
) {
  def toMaven = MavenReference(groupId, artifactId, version)
}

// case class JvmDependency(mavenReference: MavenReference)
// case class ScalaDependency()

case class MavenReference(groupId: String, artifactId: String, version: String)

case class Version(_1: Int, _2: Int, _3: Int, extra: String = "") {
  def binary: String = s"${_1}.${_2}" // like 2.11
  override def toString: String = s"${_1}.${_2}.${_3}$extra"
}

sealed trait ScalaTargetType {
  def logoFileName: String
  def label: String
  def scalaTarget: ScalaTarget
}
object ScalaTargetType {
  case object JVM extends ScalaTargetType {
    def logoFileName = "smooth-spiral.png"
    def label = "Scala"
    def scalaTarget = ScalaTarget.Jvm()
  }
  case object Dotty extends ScalaTargetType {
    def logoFileName = "dotty3.svg"
    def label = "Dotty"
    def scalaTarget = ScalaTarget.Dotty
  }
  case object JS extends ScalaTargetType {
    def logoFileName = "scala-js.svg"
    def label = "Scala.Js"
    def scalaTarget = ScalaTarget.Js()
  }
  // case object Native extends ScalaTargetType {
  //   def logoFileName = "native2.png"
  //   def label = "Native"
  //   def scalaTarget = ScalaTarget.Native
  // }
}

sealed trait ScalaTarget {
  def targetType: ScalaTargetType
  def scaladexRequest: Map[String, String]
}
object ScalaTarget {
  private val defaultScalaVersion = Version(2, 11, 8)
  private val defaultScalaJsVersion = Version(0, 6, 12)

  case class Jvm(scalaVersion: Version = defaultScalaVersion) extends ScalaTarget {
    def targetType = ScalaTargetType.JVM
    def scaladexRequest = Map("target" -> "JVM", "scalaVersion" -> scalaVersion.binary)
  }
  case class Js(scalaVersion: Version = defaultScalaVersion, 
                scalaJsVersion: Version = defaultScalaJsVersion) extends ScalaTarget {

    def targetType = ScalaTargetType.JS
    def scaladexRequest = Map(
      "target" -> "JS",
      "scalaVersion" -> scalaVersion.binary,
      "scalaJsVersion" -> scalaJsVersion.binary
    )
  }
  // case object Native extends ScalaTarget {
  //   def targetType = ScalaTargetType.Native
  // }
  case object Dotty extends ScalaTarget {
    def targetType = ScalaTargetType.Dotty
    def scaladexRequest = Map("target" -> "JVM", "scalaVersion" -> "2.11")
  }
}

// input
case class Inputs(
  code: String = "",
  target: ScalaTarget = ScalaTarget.Jvm(),
  libraries: Set[MavenReference] = Set()
)

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