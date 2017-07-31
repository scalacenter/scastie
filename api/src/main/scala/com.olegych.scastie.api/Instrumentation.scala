package com.olegych.scastie.api

import com.olegych.scastie.proto

case class Position(start: Int, end: Int) {
  def toProto: proto.Position = proto.Position(start, end)
}

case class Instrumentation(position: Position, render: Render) {
  def toProto: proto.Instrumentation = {
    proto.Instrumentation(
      position = position.toProto,
      render = render.toProto
    )
  }
}

sealed trait Render {
  def toProto: proto.Instrumentation.Render
}

case class Value(value: String, tpe: String) extends Render {
  def toProto: proto.Instrumentation.Render = {
    proto.Instrumentation.Render.WrapValue(
      proto.Instrumentation.Value(
        value = value,
        tpe = tpe
      )
    )
  }
}
case class Html(content: String, folded: Boolean = false) extends Render {
  def toProto: proto.Instrumentation.Render = {
    proto.Instrumentation.Render.WrapHtml(
      proto.Instrumentation.Html(
        content = content,
        folded = folded
      )
    )
  }

  def stripMargin: Html = copy(content = content.stripMargin)
  def fold: Html = copy(folded = true)
}
case class AttachedDom(uuid: String, folded: Boolean = false) extends Render {
  def toProto: proto.Instrumentation.Render = {
    proto.Instrumentation.Render.WrapAttachedDom(
      proto.Instrumentation.AttachedDom(
        uuid = proto.UUID(uuid),
        folded = folded
      )
    )
  }

  def fold: AttachedDom = copy(folded = true)
}