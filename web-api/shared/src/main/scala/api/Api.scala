package api

import scala.concurrent.Future

trait Api {
  def run(code: String,
          sbtConfig: String,
          target: ScalaTargetType): Future[Long]
  def fetch(id: Long): Future[Option[Paste]]
}

case class Paste(
  id: Long,
  code: String,
  sbt: String
)

sealed trait ScalaTargetType
object ScalaTargetType {
  case object JVM    extends ScalaTargetType
  case object Dotty  extends ScalaTargetType
  case object JS     extends ScalaTargetType
  case object Native extends ScalaTargetType
}

case class PasteProgress(
    id: Long,
    output: String,
    compilationInfos: List[Problem],
    instrumentations: List[Instrumentation]
)

sealed trait Severity
case object Info    extends Severity
case object Warning extends Severity
case object Error   extends Severity

case class Problem(severity: Severity, offset: Option[Int], message: String)

case class Position(start: Int, end: Int)
case class Instrumentation(position: Position, render: Render)

sealed trait Render
case class Value(v: String, className: String) extends Render

case class Markdown(a: String, folded: Boolean = false) extends Render {
  def stripMargin = Markdown(a.stripMargin)
  def fold        = copy(folded = true)
}
case class Html(a: String, folded: Boolean = false) extends Render {
  def stripMargin = copy(a = a.stripMargin)
  def fold        = copy(folded = true)
}
