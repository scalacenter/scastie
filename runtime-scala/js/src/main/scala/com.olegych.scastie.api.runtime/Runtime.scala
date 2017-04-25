package com.olegych.scastie.api
package runtime

import org.scalajs.dom.raw.HTMLElement

import java.util.UUID

import upickle.default.{write => uwrite}

import java.awt.image.BufferedImage

object Runtime extends SharedRuntime {
  def write(in: Either[Option[RuntimeError], List[Instrumentation]]): String = {
    uwrite(in)
  }
  def render[T: pprint.PPrint](a: T, attach: HTMLElement => UUID)(
      implicit tp: pprint.TPrint[T]
  ): Render = {
    a match {
      case element: HTMLElement => {
        val uuid = attach(element)
        AttachedDom(uuid.toString)
      }
      case _ => super.render(a)
    }
  }

  def image(path: String): Html = 
    throw new Exception("image(path: String): Html works only on the jvm")

  def toBase64(in: BufferedImage): Html =
    throw new Exception("toBase64(in: BufferedImage): Html works only on the jvm")
}
