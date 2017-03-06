package com.olegych.scastie.api
package runtime

import org.scalajs.dom.raw.HTMLElement

import java.util.UUID

object Runtime extends SharedRuntime {
  def render[T: pprint.PPrint](a: T, attach: HTMLElement => UUID)(implicit tp: pprint.TPrint[T]): Render = {
    a match {
      case element: HTMLElement => {
        val uuid = attach(element)
        println(println("render uuid: " + uuid.toString))
        AttachedDom(uuid.toString)
      }
      case _ => super.render(a) 
    }
  }
}
