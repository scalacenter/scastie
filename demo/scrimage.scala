// scrimage-core, scrimage-filters

import com.sksamuel.scrimage._, filter._
import java.io.{File, FileInputStream, ByteArrayOutputStream}
import javax.imageio.ImageIO
import java.util.Base64
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

def toBase64(image: Image) = {
  val os = new ByteArrayOutputStream
  val b64 = Base64.getEncoder.wrap(os)
  ImageIO.write(image.awt, "png", b64)
  val encoded = os.toString("UTF-8")
  html"""<div style="width:200px; margin:0 auto"><image src="data:image/png;base64,$encoded"></div>""".fold
}

toBase64(small)

toBase64(small.filter(SepiaFilter))
