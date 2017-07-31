package com.olegych.scastie.api.runtime

import com.olegych.scastie.api
import com.olegych.scastie.proto

import org.scalajs.dom.raw.HTMLElement

import java.util.UUID

import java.awt.image.BufferedImage

object Runtime extends SharedRuntime {
  private def convert(
    in: Either[Option[proto.RuntimeError], List[api.Instrumentation]]):
     proto.InstrumentationsJs = {

    val value =
      in match {
        case Left(err) => {
          proto.InstrumentationsJs.Value.WrapError(
            proto.InstrumentationsJs.Error(err)
          )
        }
        case Right(instrumentations) => {
          proto.InstrumentationsJs.Value.WrapInstrumentations(
            proto.Instrumentations(instrumentations = 
              instrumentations.map(_.toProto).toSet
            )
          )
        }
      }

    proto.InstrumentationsJs(value = value)
  }

  def write(in: Either[Option[proto.RuntimeError], List[api.Instrumentation]]): String = {
    jsonPbPrinter.print(convert(in))
  }

  def render[T](a: T, attach: HTMLElement => UUID)(
      implicit tp: pprint.TPrint[T]
  ): api.Render = {
    a match {
      case element: HTMLElement => {
        val uuid = attach(element)
        api.AttachedDom(uuid.toString)
      }
      case _ => super.render(a)
    }
  }

  def image(path: String): api.Html =
    throw new Exception("image(path: String): Html works only on the jvm")

  def toBase64(in: BufferedImage): api.Html =
    throw new Exception(
      "toBase64(in: BufferedImage): Html works only on the jvm"
    )
}
