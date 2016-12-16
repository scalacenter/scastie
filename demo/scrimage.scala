class Worksheet$ {
  import com.sksamuel.scrimage._
  import com.sksamuel.scrimage.filter._

  import java.io.{File, FileInputStream}

  val in    = new FileInputStream(new File("/tmp/scastie/lanzarote.jpg"))
  val image = Image.fromStream(in)
  val small = image.scaleToWidth(200)

  small.output(new File("/tmp/scastie/small.jpg"))
  api.Html("<img src='/tmp/scastie/small.jpg' alt='small lanzarote'>", true)

  small.filter(SepiaFilter).output(new File("/tmp/scastie/small-sepia.jpg"))
  api.Html(
    "<img src='/tmp/scastie/small-sepia.jpg' alt='small lanzarote sepia'>",
    true)
}
