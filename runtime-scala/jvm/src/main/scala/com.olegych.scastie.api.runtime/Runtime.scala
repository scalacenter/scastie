package com.olegych.scastie.api
package runtime

import javax.imageio.ImageIO
import java.io.{File, ByteArrayOutputStream}
import java.util.Base64
import java.awt.image.BufferedImage

object Runtime extends SharedRuntime {
  override def render[T: pprint.PPrint](
      a: T
  )(implicit tp: pprint.TPrint[T]): Render = {
    super.render(a)
  }

  def image(path: String): Html = {
    val in = ImageIO.read(new File(path))
    toBase64(in)
  }

  def toBase64(in: BufferedImage): Html = {
    val width = in.getWidth
    val os = new ByteArrayOutputStream
    val b64 = Base64.getEncoder.wrap(os)
    ImageIO.write(in, "png", b64)
    val encoded = os.toString("UTF-8")

    Html(
      s"""
      <div style="width:${width}px; margin:0 auto">
        <image src="data:image/png;base64,$encoded">
      </div>
      """,
      folded = true
    )
  }
}
