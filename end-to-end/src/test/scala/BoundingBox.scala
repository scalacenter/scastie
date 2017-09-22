import org.openqa.selenium.{WebElement, WebDriver}

import scala.language.implicitConversions

case class BoundingBox(x0: Int, y0: Int, w: Int, h: Int) {
  val x1 = x0 + w
  val y1 = y0 + h
  
  def isInside(bounds: BoundingBox): Boolean = {
    bounds.x0 <= x0 && x0 <=bounds.x1 &&
    bounds.x0 <= x1 && x1 <=bounds.x1 &&
    bounds.y0 <= y0 && y0 <=bounds.y1 &&
    bounds.y0 <= y1 && y1 <=bounds.y1
  }
}

trait BoundingBoxHelpers {
  implicit def elementToBoundingBox(element: WebElement): BoundingBox = 
    BoundingBox(
      x0 = element.getLocation.getX,
      y0 = element.getLocation.getY,
      w = element.getSize.getWidth,
      h = element.getSize.getHeight
    )

  implicit def windowToBoudingBox(window: WebDriver.Window): BoundingBox = 
    BoundingBox(
      x0 = window.getPosition.getX,
      y0 = window.getPosition.getY,
      w = window.getSize.getWidth,
      h = window.getSize.getHeight
    )
}

