package scastie.runtime

import play.api.libs.json.Json
import org.scalajs.dom.HTMLElement
import java.util.UUID
import java.awt.image.BufferedImage
import scala.reflect.ClassTag
import scastie.runtime.api._
import play.api.libs.json.OFormat

object Runtime extends SharedRuntime {
  implicit val runtimeErrorFormat: OFormat[RuntimeError] = Json.format[RuntimeError]
  implicit val scalaJsResultFormat: OFormat[ScalaJsResult] = Json.format[ScalaJsResult]

  def write(instrumentations: List[Instrumentation], error: Option[RuntimeError]): String = {
    Json.stringify(Json.toJson(ScalaJsResult(instrumentations, error)))
  }

  def render[T](a: T, attach: HTMLElement => UUID)(implicit _ct: ClassTag[T] = null): Render = {
    val ct = Option(_ct)
    a match {
      case element: HTMLElement => {
        val uuid = attach(element)
        AttachedDom(uuid.toString)
      }
      case _ => super.render(a, ct.map(_.toString).getOrElse(""))
    }
  }

  def image(path: String): Html =
    throw new Exception("image(path: String): Html works only on the jvm")

  def toBase64(in: BufferedImage): Html =
    throw new Exception(
      "toBase64(in: BufferedImage): Html works only on the jvm"
    )
}
