package org.scastie.runtime

import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream, File}
import java.util.Base64

import javax.imageio.ImageIO
import org.scastie.runtime.api._

protected[runtime] trait JvmRuntime extends SharedRuntime {

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
