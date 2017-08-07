package com.olegych.scastie.api
package runtime

import play.api.libs.json.Json

import org.scalajs.dom.raw.HTMLElement

import java.util.UUID

import java.awt.image.BufferedImage

import scala.reflect.ClassTag

object Runtime extends SharedRuntime {
  def write(in: Either[Option[RuntimeError], List[Instrumentation]]): String = {
    Json.stringify(Json.toJson(ScalaJsResult(in)))
  }
  def render[T: ClassTag](a: T, attach: HTMLElement => UUID): Render = {
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
    throw new Exception(
      "toBase64(in: BufferedImage): Html works only on the jvm"
    )
}
