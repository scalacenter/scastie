import org.scalajs.dom.raw.HTMLImageElement
import org.scalajs.dom

val img = dom.document.createElement("img").asInstanceOf[HTMLImageElement]
img.alt = "such dog" 
img.src = "http://static5.businessinsider.com/image/52b2df16eab8ea421ff15454-500-379/doge.jpg"
img

import org.scalajs.dom.raw.HTMLPreElement
import org.scalajs.dom

val pre = dom.document.createElement("pre").asInstanceOf[HTMLPreElement]
pre.innerHTML = "1+1"
pre

throw new Exception("== Bug ==")

scala.util.Random.nextInt()