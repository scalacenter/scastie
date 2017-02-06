package com.olegych.scastie.api
package runtime

import upickle.Js._
object Runtime {
  def render[T](a: T)(implicit ct: scala.reflect.ClassTag[T]): Render = {
    a match {
      case html: Html => html
      case v => Value(a.toString.take(1000), ct.toString)
    }
  }

  def write(instrumentations: List[Instrumentation]): String = {
    def write2(bool: Boolean): upickle.Js.Value =
      if (bool) True
      else False
    def write(render: Render): Obj = {
      render match {
        case Value(v, className) =>
          Obj(
            "$type" -> Str("api.Value"),
            "v" -> Str(v),
            "className" -> Str(className)
          )

        case Html(a, folded) =>
          Obj(
            "$type" -> Str("api.Html"),
            "a" -> Str(a),
            "folded" -> write2(folded)
          )
      }
    }

    upickle.json.write(Arr(instrumentations.map { instrumentation =>
      Obj(
        "position" -> Obj(
          "start" -> Num(instrumentation.position.start.toDouble),
          "end" -> Num(instrumentation.position.end.toDouble)
        ),
        "render" -> write(instrumentation.render)
      )
    }: _*))
  }
}
