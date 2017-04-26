// scrimage-core, scrimage-filters

import com.sksamuel.scrimage._, filter._
import java.io.{File, FileInputStream}
import java.net.URL
import java.nio.file.{Files, Paths}
import scala.util.Try

// Download image to cache
val dest = Paths.get("/tmp/scastie/lanzarote.jpg")
if (!Files.exists(dest)) {
  Files.createDirectories(dest.getParent)
  val url = new URL("https://github.com/sksamuel/scrimage/blob/master/scrimage-core/src/test/resources/lanzarote.jpg?raw=true")
  Try(url.openStream()).foreach(src => Files.copy(src, dest))
}
val image = Image.fromStream(new FileInputStream(new File("/tmp/scastie/lanzarote.jpg")))
val small = image.scaleToWidth(200)


toBase64(small)

toBase64(small.filter(SepiaFilter))
