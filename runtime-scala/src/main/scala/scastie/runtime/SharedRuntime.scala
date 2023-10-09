package scastie.runtime

import play.api.libs.json.Json
import scastie.runtime.api._
import play.api.libs.json.OFormat

protected[runtime] trait SharedRuntime {
  implicit val instrumentationFormat: OFormat[Instrumentation] = Json.format[Instrumentation]
  implicit val positionFormat: OFormat[Position] = Json.format[Position]
  implicit val valueFormat: OFormat[Value] = Json.format[Value]
  implicit val htmlFormat: OFormat[Html] = Json.format[Html]
  implicit val attachedDomFormat: OFormat[AttachedDom] = Json.format[AttachedDom]
  implicit val renderFormat: OFormat[Render] = Json.format[Render]

  def write(instrumentations: List[Instrumentation]): String = {
    if (instrumentations.isEmpty) "" else Json.stringify(Json.toJson(instrumentations))
  }

  private val maxValueLength = 500

  private def show[A](a: A): String =
    if (a == null) "null"
    else a.toString

  protected[runtime] def render[T](a: T, typeName: String): Render = {
    a match {
      case html: Html => html
      case v =>
        val vs = show(v)
        val out =
          if (vs.size > maxValueLength) vs.take(maxValueLength) + "..."
          else vs

        Value(out, typeName.replace(Instrumentation.instrumentedObject + ".", ""))
    }
  }
}
