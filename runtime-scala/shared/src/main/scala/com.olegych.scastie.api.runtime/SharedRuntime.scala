package com.olegych.scastie.api.runtime

import com.olegych.scastie.api
import com.trueaccord.scalapb.json.{Printer => JsonPbPrinter}

protected[runtime] trait SharedRuntime {
  protected val jsonPbPrinter = new JsonPbPrinter()

  protected[runtime] def render[T](
      a: T
  )(implicit tp: pprint.TPrint[T]): api.Render = {
    a match {
      case html: api.Html => html
      case v          => api.Value(pprint.tokenize(v).mkString, tp.render)
    }
  }
}
