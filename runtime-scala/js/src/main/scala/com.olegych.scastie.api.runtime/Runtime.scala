package com.olegych.scastie.api
package runtime

import org.scalajs.dom.raw.HTMLElement
import upickle.default.{write => uwrite}

object Runtime extends SharedRuntime {
  def render[T: pprint.PPrint](a: T, attach: HTMLElement => Unit)(implicit tp: pprint.TPrint[T]): Render = {
    a match {
      case element: HTMLElement => {
        attach(element)
        AttachedDom(element)
      }
      case _ => super.render(a) 
    }
  }
}
