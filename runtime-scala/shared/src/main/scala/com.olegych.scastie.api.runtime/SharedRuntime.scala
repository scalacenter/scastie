package com.olegych.scastie.api
package runtime

import play.api.libs.json.Json

import scala.reflect.ClassTag

protected[runtime] trait SharedRuntime {
  def write(instrumentations: List[Instrumentation]): String = {
    Json.stringify(Json.toJson(instrumentations))
  }

  private val maxValueLength = 500

  protected[runtime] def render[T](a: T)(implicit ct: ClassTag[T]): Render = {    
    a match {
      case html: Html => html
      case v          => {
        val vs = v.toString
        val out =
          if(vs.size > maxValueLength) vs.take(maxValueLength) + "..."
          else vs

        Value(out, ct.toString)
      }
    }
  }
}
