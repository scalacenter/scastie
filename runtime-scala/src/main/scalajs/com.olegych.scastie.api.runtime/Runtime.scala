package com.olegych.scastie.api
package runtime

import java.awt.image.BufferedImage
import java.util.UUID
import scala.reflect.ClassTag

import org.scalajs.dom.HTMLElement
import play.api.libs.json.Json

object Runtime extends SharedRuntime {

  def write(in: Either[Option[RuntimeError], List[Instrumentation]]): String = {
    Json.stringify(Json.toJson(ScalaJsResult(in)))
  }

  def render[T](a: T, attach: HTMLElement => UUID)(
    implicit _ct: ClassTag[T] = null
  ): Render = {
    val ct = Option(_ct)
    a match {
      case element: HTMLElement => {
        val uuid = attach(element)
        AttachedDom(uuid.toString)
      }
      case _ => super.render(a, ct.map(_.toString).getOrElse(""))
    }
  }

  def image(path: String): Html = throw new Exception("image(path: String): Html works only on the jvm")

  def toBase64(in: BufferedImage): Html = throw new Exception(
    "toBase64(in: BufferedImage): Html works only on the jvm"
  )

}
