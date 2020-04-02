package com.olegych.scastie.api

import play.api.libs.json._

object Render {
  implicit object RenderFormat extends Format[Render] {
    private val formatValue = Json.format[Value]
    private val formatHtml = Json.format[Html]
    private val formatAttachedDom = Json.format[AttachedDom]

    def writes(render: Render): JsValue = {
      render match {
        case v: Value =>
          formatValue.writes(v) ++ JsObject(Seq("tpe" -> JsString("Value")))
        case h: Html =>
          formatHtml.writes(h) ++ JsObject(Seq("tpe" -> JsString("Html")))
        case a: AttachedDom =>
          formatAttachedDom.writes(a) ++ JsObject(Seq("tpe" -> JsString("AttachedDom")))
      }
    }

    def reads(json: JsValue): JsResult[Render] = {
      json match {
        case obj: JsObject =>
          val vs = obj.value
          vs.get("tpe").orElse(vs.get("$type")) match {
            case Some(tpe) =>
              tpe match {
                case JsString("Value")       => formatValue.reads(json)
                case JsString("Html")        => formatHtml.reads(json)
                case JsString("AttachedDom") => formatAttachedDom.reads(json)
                case _                       => JsError(Seq())
              }
            case None => JsError(Seq())
          }
        case _ => JsError(Seq())
      }
    }
  }
}

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
  implicit val formatInstrumentation: OFormat[Instrumentation] =
    Json.format[Instrumentation]
}

case class Instrumentation(
    position: Position,
    render: Render
)
