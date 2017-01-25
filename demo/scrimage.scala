
// Does not work on scastie.scala-lang.org because the running instance and the 
// web server are on different machines

/*
libraryDependencies ++= Seq(
  "com.sksamuel.scrimage" %% "scrimage-core" % "2.1.8" ,
  "com.sksamuel.scrimage" %% "scrimage-filters" % "2.1.8" 
)
*/

import com.sksamuel.scrimage._
import com.sksamuel.scrimage.filter._
import java.io.{File, FileInputStream}
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

small.output(new File("/tmp/scastie/small.jpg"))
html"<img src='/tmp/scastie/small.jpg' alt='small lanzarote'>".fold

small.filter(SepiaFilter).output(new File("/tmp/scastie/small-sepia.jpg"))
html"<img src='/tmp/scastie/small-sepia.jpg' alt='small lanzarote sepia'>".fold
