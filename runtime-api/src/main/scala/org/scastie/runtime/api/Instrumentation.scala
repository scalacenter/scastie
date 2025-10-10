package org.scastie.runtime.api

import StringUtils._

sealed trait Render {
  def asJsonString: String = this match {
    case Value(v, className) => s"""{"Value":{"v":"${v.escaped}","className":"${className.escaped}"}}"""
    case Html(a, folded) => s"""{"Html":{"a":"${a.escaped}","folded":$folded}}"""
    case AttachedDom(uuid, folded) => s"""{"AttachedDom":{"uuid":"$uuid","folded":$folded}}"""
  }
}

object Render {
  /* Convert JSON string to Render */
  /* Example: {"Value":{"v":"some value","className":"some class"}} -> Value("some value", "some class") */
  def fromJsonString(json: String): Option[Render] = {
    if (json.startsWith("""{"Value":""")) {
      val vPattern         = """"v":"(.*?)"""".r
      val classNamePattern = """"className":"(.*?)"""".r
      for {
        v         <- vPattern.findFirstMatchIn(json).map(_.group(1))
        className <- classNamePattern.findFirstMatchIn(json).map(_.group(1))
      } yield Value(v, className)
    } else if (json.startsWith("""{"Html":""")) {
      val aPattern      = """"a":"(.*?)"""".r
      val foldedPattern = """"folded":(true|false)""".r
      for {
        a      <- aPattern.findFirstMatchIn(json).map(_.group(1))
        folded <- foldedPattern.findFirstMatchIn(json).map(_.group(1).toBoolean)
      } yield Html(a, folded)
    } else if (json.startsWith("""{"AttachedDom":""")) {
      val uuidPattern   = """"uuid":"(.*?)"""".r
      val foldedPattern = """"folded":(true|false)""".r
      for {
        uuid   <- uuidPattern.findFirstMatchIn(json).map(_.group(1))
        folded <- foldedPattern.findFirstMatchIn(json).map(_.group(1).toBoolean)
      } yield AttachedDom(uuid, folded)
    } else None
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

case class Instrumentation(position: Position, render: Render) {
  def asJsonString: String = s"""{"position":${position.asJsonString},"render":${render.asJsonString}}"""
}

object Instrumentation {
  val instrumentedObject = "Playground"

  def fromJsonString(json: String): Option[Instrumentation] = {
    val posPattern    = """"position":(\{[^\}]+\})""".r
    val renderPattern = """"render":(\{.*\})\s*\}""".r
    for {
      posJson    <- posPattern.findFirstMatchIn(json).map(_.group(1))
      renderJson <- renderPattern.findFirstMatchIn(json).map(_.group(1))
      pos        <- Position.fromJsonString(posJson)
      render     <- Render.fromJsonString(renderJson)
    } yield Instrumentation(pos, render)
  }
}

case class Position(start: Int, end: Int) {
  def asJsonString: String = s"""{"start":$start,"end":$end}"""
}

object Position {
  def fromJsonString(json: String): Option[Position] = {
    val startPattern = """"start":(\d+)""".r
    val endPattern   = """"end":(\d+)""".r
    for {
      start <- startPattern.findFirstMatchIn(json).map(_.group(1).toInt)
      end   <- endPattern.findFirstMatchIn(json).map(_.group(1).toInt)
    } yield Position(start, end)
  }
}

case class Binder(pos: Position, render: Render)
case class Statement(binders: List[Binder], position: Position)
