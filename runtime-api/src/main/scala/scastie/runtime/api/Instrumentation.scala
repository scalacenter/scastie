package scastie.runtime.api

sealed trait Render

case class Value(v: String, className: String) extends Render
case class Html(a: String, folded: Boolean = false) extends Render {
  def stripMargin: Html = copy(a = a.stripMargin)
  def fold: Html = copy(folded = true)
}
case class AttachedDom(uuid: String, folded: Boolean = false) extends Render {
  def fold: AttachedDom = copy(folded = true)
}

object Instrumentation {
  val instrumentedObject = "Playground"
}

case class Instrumentation(position: Position, render: Render)

case class Position(start: Int, end: Int)
