
/*
resolvers += Resolver.bintrayRepo("underscoreio", "training")

libraryDependencies ++= Seq(
  "underscoreio" %% "doodle" % "0.6.5",
  "com.sksamuel.scrimage" %% "scrimage-core" % "2.1.8",
  "com.sksamuel.scrimage" %% "scrimage-filters" % "2.1.8"
)

*/
import com.sksamuel.scrimage._
import com.sksamuel.scrimage.filter._

import java.io.ByteArrayOutputStream
import java.io.{File, FileInputStream}

import javax.imageio.ImageIO

import java.util.Base64
import java.net.URL
import java.nio.file.{Files, Paths}

import scala.util.Try

// Download image to cache
val dest = Paths.get("/tmp/scastie/lanzarote.jpg")
if (!Files.exists(dest)) {
  println("downloading")
  Files.createDirectories(dest.getParent)
  val url = new URL(
    "https://github.com/sksamuel/scrimage/blob/master/scrimage-core/src/test/resources/lanzarote.jpg?raw=true")
  Try(url.openStream()).foreach(src => Files.copy(src, dest))
}

val image =
  Image.fromStream(new FileInputStream(new File("/tmp/scastie/lanzarote.jpg")))
val small = image.scaleToWidth(200)

def toBase64(image: Image): api.Html = {
  val os = new ByteArrayOutputStream
  val b64 = Base64.getEncoder.wrap(os)
  ImageIO.write(image.awt, "png", b64)
  val encoded = os.toString("UTF-8")
  html"""<image src="data:image/png;base64,$encoded">""".fold
}

toBase64(small)

toBase64(small.filter(SepiaFilter))
