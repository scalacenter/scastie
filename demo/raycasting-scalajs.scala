
demo

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import math._
import scala.scalajs.js
import org.scalajs.dom.html.{Canvas => JsCanvas}
import org.scalajs.dom
import scala.language.{postfixOps, implicitConversions}

object Vec{
  case class Unit(x: Double, y: Double, z: Double)
  implicit def normalizer(v: Vec) = {
    val l = v.magnitude
    new Unit(v.x / l, v.y / l, v.z / l)
  }
  implicit def denormalizer(v: Vec.Unit) = new Vec(v.x, v.y, v.z)
  implicit def pointify[X: Numeric, Y: Numeric, Z: Numeric](x: (X, Y, Z)): Vec = Vec(
    implicitly[Numeric[X]].toDouble(x._1),
    implicitly[Numeric[Y]].toDouble(x._2),
    implicitly[Numeric[Z]].toDouble(x._3)
  )
  implicit def pointify2[X: Numeric, Y: Numeric, Z: Numeric](x: (X, Y, Z)): Vec.Unit = Vec.normalizer(x)
}

case class Vec(x: Double, y: Double, z: Double){
  def magnitude = sqrt(this dot this)
  def +(o: Vec) = Vec(x + o.x, y + o.y, z + o.z)
  def -(o: Vec) = Vec(x - o.x, y - o.y, z - o.z)
  def *(o: Vec) = Vec(x * o.x, y * o.y, z * o.z)
  def *(f: Double) = Vec(x * f, y * f, z * f)
  def /(f: Double) = Vec(x / f, y / f, z / f)
  def dot(o: Vec) = x * o.x + y * o.y + z * o.z
  def cross(o: Vec) = Vec(
    y * o.z - z * o.y,
    z * o.x - x * o.z,
    x * o.y - y * o.x
  )

  def normalized = this / magnitude
  def reflectThrough(normal: Vec) = this - normal * (this dot normal) * 2
}

val Epsilon = 0.00001
type Color = Vec
val Color = Vec

def demo = {
  /**
   * A simple ray tracer, taken from the PyPy benchmarks
   *
   * https://bitbucket.org/pypy/benchmarks/src/846fa56a282b/own/raytrace-simple.py?at=default
   *
   * Half the lines of code
   *
   * Author: https://github.com/lihaoyi
   */
  val canvas = dom.document.createElement("canvas").asInstanceOf[JsCanvas]

  canvas.width = dom.document.body.clientWidth * 80
  canvas.height = dom.document.body.clientHeight * 80

  val renderer = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  val r = new util.Random(16314302)
  val spiral = for (i <- 0 until 11) yield {
    val theta = i * (i + 5) * Pi / 100 + 0.3
    val center = (0 - 4 * sin(theta), 1.5 - i / 2.0, 0 - 4 * cos(theta))
    val form = Sphere(center, 0.3 + i * 0.1)
    val surface = Flat((i / 6.0, 1 - i / 6.0, 0.5))
    (form, surface)
  }

  def rand(d: Double) = (r.nextDouble() - 0.5) * d * 2

  val drops = Array(
    Sphere((2.5, 2.5, -8), 0.3),
    Sphere((1.5, 2.2, -7), 0.25),
    Sphere((-1.3, 0.8, -8.5), 0.15),
    Sphere((0.5, -2.5, -7.5), 0.2),
    Sphere((-1.8, 2.3, -7.5), 0.3),
    Sphere((-1.8, -2.3, -7.5), 0.3),
    Sphere((1.3, 0.0, -8), 0.25)
  ).map(_ -> Refractor())

  val s = new Scene(
    objects = Array(
      Sphere((0, 0, 0), 2) -> Flat((1, 1, 1), specularC = 0.6, lambertC = 0.4),
      Plane((0, 4, 0), (0, 1, 0)) -> Checked(),
      Plane((0, -4, 0), (0, 1, 0)) -> Flat((0.9, 1, 1)),
      Plane((6, 0, 0), (1, 0, 0)) -> Flat((1, 0.9, 1)),
      Plane((-6, 0, 0), (1, 0, 0)) -> Flat((1, 1, 0.9)),
      Plane((0, 0, 6), (0, 0, 1)) -> Flat((0.9, 0.9, 1))
    ) ++ spiral ++ drops,
    lightPoints = Array(
      Light((0, -3, 0), (3, 3, 0)),
      Light((3, 3, 0), (0, 3, 3)),
      Light((-3, 3, 0), (3, 0, 3))
    ),
    position = (0, 0, -15),
    lookingAt = (0, 0, 0),
    fieldOfView = 45.0
  )

  val c = new Canvas{
    val width = math.min(canvas.width.toInt, canvas.height.toInt)
    val height = math.min(canvas.width.toInt, canvas.height.toInt)
    val data = renderer.getImageData(0, 0, canvas.width, canvas.height)
    def save(y: Int): Unit = {
      renderer.putImageData(data, 0, 0, 0, y-1, width, 1)
    }

    def plot(x: Int, y: Int, rgb: Color): Unit = {
      val index = (y * data.width + x) * 4
      data.data(index+0) = (rgb.x * 255).toInt
      data.data(index+1) = (rgb.y * 255).toInt
      data.data(index+2) = (rgb.z * 255).toInt
      data.data(index+3) = 255
    }
  }

  s.render(c)

  canvas
}

abstract class Form{
  def intersectionTime(ray: Ray): Double
  def normalAt(p: Vec): Vec
}

case class Sphere(center: Vec, radius: Double) extends Form{
  def intersectionTime(ray: Ray) = {
    val cp = center - ray.point
    val v = cp dot ray.vector
    val d = radius * radius - ((cp dot cp) - v * v)
    if (d < 0) -1
    else v - sqrt(d)
  }
  def normalAt(p: Vec) = (p - center).normalized
}

case class Plane(point: Vec, normal: Vec.Unit) extends Form{
  def intersectionTime(ray: Ray) = {
    val v = ray.vector dot normal
    if (v != 0) ((point - ray.point) dot normal) / v
    else -1
  }
  def normalAt(p: Vec) = normal
}

case class Ray(point: Vec, vector: Vec.Unit){
  def pointAtTime(t: Double) = point + vector * t
}

case class Light(center: Vec, color: Color)

abstract class Surface{
  def colorAt(scene: Scene, ray: Ray, p: Vec, normal: Vec.Unit, depth: Int): Color
}

abstract class SolidSurface extends Surface{
  def baseColorAt(p: Vec): Color
  def specularC: Double
  def lambertC: Double
  val ambientC = 1.0 - specularC - lambertC
  def colorAt(scene: Scene, ray: Ray, p: Vec, normal: Vec.Unit, depth: Int): Color = {
    val b = baseColorAt(p)

    val specular = {
      val reflectedRay = Ray(p, ray.vector.reflectThrough(normal))
      val reflectedColor = scene.rayColor(reflectedRay, depth)
      reflectedColor * specularC
    }

    val lambert = {
      var lambertAmount = Vec(0, 0, 0)
      for (i <- 0 until scene.lightPoints.length) {
        val light = scene.lightPoints(i)
        if (scene.lightIsVisible(light.center, p)) {
          val d = p - light.center
          val dLength = d.magnitude
          val contribution = light.color * abs(d dot normal / (dLength * dLength))

          lambertAmount += contribution
        }
      }
      b * lambertAmount * lambertC
    }

    val ambient = b * ambientC

    specular + lambert + ambient
  }
}

case class Refractor(refractiveIndex: Double = 0.5) extends Surface{
  def colorAt(scene: Scene, ray: Ray, p: Vec, normal: Vec.Unit, depth: Int): Color = {
    val r = if ((normal dot ray.vector) < 0)
      refractiveIndex
    else
      1.0 / refractiveIndex

    val c = (normal * -1) dot ray.vector

    val sqrtValue = 1 - r * r * (1 - c * c)

    if (sqrtValue > 0){
      val refracted = ray.vector * r + normal * (r * c - sqrt(sqrtValue))
      scene.rayColor(Ray(p, refracted), depth)
    }else{
      val perp = ray.vector dot normal
      val reflected: Vec = Vec.denormalizer(ray.vector) + normal * 2 * perp
      scene.rayColor(Ray(p, reflected), depth)
    }
  }
}

case class Flat(baseColor: Color = Color(1, 1, 1),
                specularC: Double = 0.3,
                lambertC: Double = 0.6) extends SolidSurface{

  def baseColorAt(p: Vec) = baseColor

}

case class Checked(baseColor: Color = Color(1, 1, 1),
                   specularC: Double = 0.3,
                   lambertC: Double = 0.6,
                   otherColor: Color = (0, 0, 0),
                   checkSize: Double = 1) extends SolidSurface{
  override def baseColorAt(p: Vec) = {
    val v = p * (1.0 / checkSize)

    def f(x: Double) = (abs(x) + 0.5).toInt
    if ((f(v.x) + f(v.y) + f(v.z)) % 2 == 1) otherColor
    else baseColor
  }
}

abstract class Canvas{
  def width: Int
  def height: Int
  def save(y: Int): Unit
  def plot(x: Int, y: Int, rgb: Color)
}

class Scene(objects: Array[(Form, Surface)],
            val lightPoints: Array[Light],
            position: Vec,
            lookingAt: Vec,
            fieldOfView: Double){

  def lightIsVisible(l: Vec, p: Vec) = {
    val ray = Ray(p, l - p)
    val length = (l - p).magnitude
    var visible = true
    for (i <- 0 until objects.length){
      val (o, s) = objects(i)
      val t = o.intersectionTime(ray)
      if (t > Epsilon && t < length - Epsilon){
        visible = false
      }
    }
    visible
  }

  def rayColor(ray: Ray, depth: Int): Color = {
    if (depth > 3) (0, 0, 0)
    else{
      var (minT, minO, minS) = (-1.0, null: Form, null: Surface)
      for(i <- 0 until objects.length){
        val (o, s) = objects(i)
        val t = o.intersectionTime(ray)
        if (t > Epsilon && (t < minT || minT < 0)){
          minT = t
          minO = o
          minS = s
        }
      }
      minT match{
        case -1 => (0, 0, 0)
        case t =>
          val p = ray.pointAtTime(minT)
          minS.colorAt(this, ray, p, minO.normalAt(p), depth + 1)
      }
    }
  }

  def render(canvas: Canvas) = {
    val fovRadians = Pi * (fieldOfView / 2.0) / 180.0
    val halfWidth = tan(fovRadians)
    val halfHeight = halfWidth
    val width = halfWidth * 2
    val height=  halfHeight * 2
    val pixelWidth = width / (canvas.width - 1)
    val pixelHeight = height / (canvas.height - 1)
    val eye = Ray(position, lookingAt - position)
    val vpRight = eye.vector.cross((0, 1, 0)).normalized
    val vpUp = vpRight.cross(eye.vector).normalized

    var y = 0;
    lazy val interval: Int = dom.window.setInterval({ () => 
      for (x <- 0 until canvas.width){
        val xcomp = vpRight * (x * pixelWidth - halfWidth)
        val ycomp = vpUp * (y * pixelHeight - halfHeight)
        val ray = Ray(eye.point, xcomp + ycomp + eye.vector)
        val color = rayColor(ray, 0)
        canvas.plot(x, y, color)
      }
      canvas.save(y)
      if (y > canvas.height) dom.window.clearInterval(interval)
      y+= 1
      
    }, 0)
    interval
  }
}
