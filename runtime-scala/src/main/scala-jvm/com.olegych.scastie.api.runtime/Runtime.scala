package com.olegych.scastie.api
package runtime

import javax.imageio.ImageIO
import java.io.{ByteArrayOutputStream, File}
import java.util.Base64
import java.awt.image.BufferedImage

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object Runtime extends SharedRuntime {
  def render[T](a: T)(implicit _ct: ClassTag[T] = null, _tt: TypeTag[T] = null): Render = {
    val ct = Option(_ct)
    val tt = Option(_tt)
    super.render(a, tt.map(_.tpe.toString).orElse(ct.map(_.toString)).getOrElse(""))
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
