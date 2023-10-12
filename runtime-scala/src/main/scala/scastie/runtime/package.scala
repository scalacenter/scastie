package org.scastie

import java.awt.image.BufferedImage

package object runtime {

  val Html: api.Html.type = api.Html

  def image(path: String): api.Html = Runtime.image(path)
  def toBase64(in: BufferedImage): api.Html = Runtime.toBase64(in)

  implicit class HtmlHelper(val sc: StringContext) extends AnyVal {
    def html(args: Any*) = Html(sc.s(args: _*))
    def htmlRaw(args: Any*) = Html(sc.raw(args: _*))
  }

  trait ScastieApp extends App
}
