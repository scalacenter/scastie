package com.olegych.scastie.api

case class Position(start: Int, end: Int)
case class Instrumentation(position: Position, render: Render)

sealed trait Render
case class Value(value: String, tpe: String) extends Render
case class Html(content: String, folded: Boolean = false) extends Render {
  def stripMargin: Html = copy(content = content.stripMargin)
  def fold: Html = copy(folded = true)
}
case class AttachedDom(uuid: String, folded: Boolean = false) extends Render {
  def fold: AttachedDom = copy(folded = true)
}
