package com.olegych.scastie.api
package runtime

import upickle.default.{write => uwrite}

protected[runtime] trait SharedRuntime {
  def write(instrumentations: List[Instrumentation]): String = {
    uwrite(instrumentations)
  }

  protected[runtime] def render[T: pprint.PPrint](a: T)(
      implicit tp: pprint.TPrint[T]): Render = {
    import pprint.Config.Defaults._
    a match {
      case html: Html => html
      case v => Value(pprint.tokenize(v).mkString, tp.render)
    }
  }
}
