/***

"underscoreio" %% "doodle" % "0.8.2"

resolvers += Resolver.bintrayRepo("underscoreio", "training")

***/

import doodle.core._
import doodle.core.Image._
import doodle.random._
import doodle.syntax._
import doodle.jvm._
import doodle.backend._
import doodle.examples._
import com.olegych.scastie.api.Html
import de.erichseifert.vectorgraphics2d.SVGGraphics2D
import java.io.ByteArrayOutputStream

def renderSvg(image: Image): Html = {
  val metrics = Java2D.bufferFontMetrics
  val dc = DrawingContext.blackLines
  val finalised = Finalised.finalise(image, dc, metrics)

  val bb = finalised.boundingBox
  val pageCenter = Point.cartesian(bb.width / 2, bb.height / 2)
  val graphics = new SVGGraphics2D(0, 0, bb.width, bb.height)
  val canvas = new Java2DCanvas(graphics, bb.center, pageCenter)

  Render.render(canvas, finalised)
  val out = new ByteArrayOutputStream
  graphics.writeTo(out)
  html"${out.toString("UTF-8")}"
}

def triangles (r :Double, n :Int, c1 :Color, c2 :Color, c3 :Color) :Image = {
    if (n == 1) triangle(r, r * math.sqrt(3)/2) fillColor c1.alpha(0.4.normalized) lineColor Color.white
    else triangles(r/2, n - 1, c1.alpha(0.4.normalized), c2, c3) above
      (triangles(r/2, n - 1, c2.alpha(0.4.normalized), c3, c1) beside
        triangles(r/2, n - 1, c3.alpha(0.4.normalized), c1, c2))
  }

  def rotation (shape :Image, n :Int, d :Int) :Image = {
    if (n == 1) shape
    else {
      shape.rotate((n*d).degrees) on rotation(shape, n-1, d)
    }
  }

  def backShape :Image = {
    def circle(size :Int, color :Color) :Image = 
      Image.circle(size).lineWidth(3.0).lineColor(color)
      
    def fadeCircles(n :Int, size :Int, color :Color) :Image = n match {
      case 0 => Image.empty
      case n => circle(size, color) on fadeCircles(n-1, size+7, color.fadeOutBy(0.04.normalized))
    }
    fadeCircles(60, 20, Color.lightSkyBlue)
  }
  
  
  val interestingShape = triangles(500, 8, Color.red, Color.yellow, Color.blue.spin(-5.degrees))
  val background = rectangle(1000, 1000) fillColor Color.darkBlue
  val thisIsIt = rotation(interestingShape, 28, 15) on backShape on background
  
  renderSvg(thisIsIt)
}
