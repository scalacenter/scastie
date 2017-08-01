package com.olegych.scastie.api.runtime

import com.olegych.scastie.api
import com.olegych.scastie.proto

import javax.imageio.ImageIO
import java.io.{File, ByteArrayOutputStream}
import java.util.Base64
import java.awt.image.BufferedImage

object Runtime extends SharedRuntime {

  private def convert(
      instrumentations: List[api.Instrumentation]
  ): proto.Instrumentations = {

    proto.Instrumentations(
      instrumentations = instrumentations.map(_.toProto).toSeq
    )
  }

  def write(instrumentations: List[api.Instrumentation]): String = {
    jsonPbPrinter.print(convert(instrumentations))
  }

  override def render[T](a: T)(implicit tp: pprint.TPrint[T]): api.Render = {
    super.render(a)
  }

  def image(path: String): api.Html = {
    val in = ImageIO.read(new File(path))
    toBase64(in)
  }

  def toBase64(in: BufferedImage): api.Html = {
    val width = in.getWidth
    val os = new ByteArrayOutputStream
    val b64 = Base64.getEncoder.wrap(os)
    ImageIO.write(in, "png", b64)
    val encoded = os.toString("UTF-8")

    api.Html(
      s"""
      <div style="width:${width}px; margin:0 auto">
        <image src="data:image/png;base64,$encoded">
      </div>
      """,
      folded = true
    )
  }
}
