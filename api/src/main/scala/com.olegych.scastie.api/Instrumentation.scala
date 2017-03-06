package com.olegych.scastie
package api

case class Instrumentation(
  position: Position,
  render: Render
)

sealed trait Render
case class Value(v: String, className: String) extends Render
case class Html(a: String, folded: Boolean = false) extends Render {
  def stripMargin = copy(a = a.stripMargin)
  def fold = copy(folded = true)
}
case class AttachedDom(uuid: String, folded: Boolean = false) extends Render {
  def fold = copy(folded = true)
}
