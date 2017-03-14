package com.olegych.scastie.api
package runtime

import org.scalajs.dom.raw.HTMLElement

import java.util.UUID

import upickle.default.{write => uwrite}

object Runtime extends SharedRuntime {
  def write(in: Either[Option[RuntimeError], List[Instrumentation]]): String = {
    uwrite(in)
  }
  def render[T: pprint.PPrint](a: T, attach: HTMLElement => UUID)(
      implicit tp: pprint.TPrint[T]): Render = {
    a match {
      case element: HTMLElement => {
        val uuid = attach(element)
        AttachedDom(uuid.toString)
      }
      case _ => super.render(a)
    }
  }
}
