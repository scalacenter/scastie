import org.scalajs.dom.raw.HTMLImageElement
import org.scalajs.dom

val img = dom.document.createElement("img").asInstanceOf[HTMLImageElement]
img.alt = "such dog" 
img.src = "https://goo.gl/a3Xr41"
img

import org.scalajs.dom.raw.HTMLPreElement
import org.scalajs.dom

val pre = dom.document.createElement("pre").asInstanceOf[HTMLPreElement]
pre.innerHTML = "1+1"
pre

throw new Exception("== Bug ==")

scala.util.Random.nextInt()