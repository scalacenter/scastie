package org.scastie.runtime

import org.scalajs.dom.HTMLElement
import java.util.UUID
import java.awt.image.BufferedImage
import scala.reflect.ClassTag
import org.scastie.runtime.api._

object Runtime extends SharedRuntime {

  def writeStatements(in: Either[Option[RuntimeError], List[Statement]]): String = {
    in match {
      case Right(statements) =>
        val instrumentations = convertToInstrumentations(statements)
        ScalaJsResult(instrumentations, None).asJsonString
      case Left(error) => ScalaJsResult(Nil, error).asJsonString
    }
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
