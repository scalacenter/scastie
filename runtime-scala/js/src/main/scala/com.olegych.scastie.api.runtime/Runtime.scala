package com.olegych.scastie.api.runtime

import com.olegych.scastie.api
import com.olegych.scastie.proto

import org.scalajs.dom.raw.HTMLElement

import java.util.UUID

import java.awt.image.BufferedImage

object Runtime extends SharedRuntime {
  
  private def convert(render: api.Render): proto.Instrumentation.Render = {
    render match {
      case api.Value(value, tpe) => {
        proto.Instrumentation.Render.WrapValue(
          proto.Instrumentation.Value(
            value = value,
            tpe = tpe
          )
        )
      }
      case api.Html(content, folded) => {
        proto.Instrumentation.Render.WrapHtml(
          proto.Instrumentation.Html(
            content = content,
            folded = folded
          )
        )
      }
      case api.AttachedDom(uuid, folded) => {
        proto.Instrumentation.Render.WrapAttachedDom(
          proto.Instrumentation.AttachedDom(
            uuid = proto.UUID(uuid),
            folded = folded
          )
        )
      }
    }
  }

  private def convert(
    instrumentation: api.Instrumentation): proto.Instrumentation = {

    val api.Instrumentation(api.Position(start, end), render) = instrumentation

    proto.Instrumentation(
      position = proto.Position(start, end),
      render = convert(render)
    )
  }

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
              instrumentations.map(convert).toSet
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
