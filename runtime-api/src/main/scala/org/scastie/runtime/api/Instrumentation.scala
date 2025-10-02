package org.scastie.runtime.api

import StringUtils._

sealed trait Render {

  def asJsonString: String = this match {
    case Value(v, className)       => s"""{"Value":{"v":"${v.escaped}","className":"${className.escaped}"}}"""
    case Html(a, folded)           => s"""{"Html":{"a":"${a.escaped}","folded":$folded}}"""
    case AttachedDom(uuid, folded) => s"""{"AttachedDom":{"uuid":"$uuid","folded":$folded}}"""
  }

}

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

case class Instrumentation(position: Position, render: Render) {
  def asJsonString: String = s"""{"position":${position.asJsonString},"render":${render.asJsonString}}"""
}

case class Position(start: Int, end: Int) {
  def asJsonString: String = s"""{"start":$start,"end":$end}"""
}

case class Binder(pos: Position, render: Render)
case class Statement(binders: List[Binder], position: Position)
