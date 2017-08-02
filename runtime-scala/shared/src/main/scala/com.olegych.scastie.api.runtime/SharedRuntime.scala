package com.olegych.scastie.api
package runtime

import play.api.libs.json.Json

import scala.reflect.ClassTag

protected[runtime] trait SharedRuntime {
  def write(instrumentations: List[Instrumentation]): String = {
    Json.stringify(Json.toJson(instrumentations))
  }

  protected[runtime] def render[T](a: T)(implicit ct: ClassTag[T]): Render = {
    a match {
      case html: Html => html
      case v          => Value(v.toString, ct.toString)
    }
  }
}
