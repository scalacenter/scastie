package com.olegych.scastie.api

import play.api.libs.json._

case class ScalaJsResult(
    in: Either[Option[RuntimeError], List[Instrumentation]]
)

object ScalaJsResult {
  private case class Error(er: Option[RuntimeError])
  private case class Instrumentations(instrs: List[Instrumentation])

  implicit object ScalaJsResultFormat extends Format[ScalaJsResult] {
    private val formatLeft = Json.format[Error]
    private val formatRight = Json.format[Instrumentations]

    def writes(result: ScalaJsResult): JsValue = {
      result.in match {
        case Left(err) =>
          formatLeft.writes(Error(err)) ++ JsObject(Seq("tpe" -> JsString("Left")))
        case Right(instrs) =>
          formatRight.writes(Instrumentations(instrs)) ++ JsObject(Seq("tpe" -> JsString("Right")))
      }
    }

    def reads(json: JsValue): JsResult[ScalaJsResult] = {
      json match {
        case obj: JsObject =>
          val vs = obj.value
          vs.get("tpe").orElse(vs.get("$type")) match {
            case Some(JsString(tpe)) =>
              tpe match {
                case "Left" =>
                  formatLeft.reads(json).map(v => ScalaJsResult(Left(v.er)))
                case "Right" =>
                  formatRight
                    .reads(json)
                    .map(v => ScalaJsResult(Right(v.instrs)))
                case _ => JsError(Seq())
              }
            case _ => JsError(Seq())
          }
        case _ => JsError(Seq())
      }
    }
  }
}
