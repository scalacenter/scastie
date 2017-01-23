/*
libraryDependencies ++= Seq(
  "com.sksamuel.scrimage" %% "scrimage-core" % "2.1.8" ,
  "com.sksamuel.scrimage" %% "scrimage-filters" % "2.1.8" 
)
*/
import com.sksamuel.scrimage._
import com.sksamuel.scrimage.filter._
import java.io.{File, FileInputStream}


val image = Image(new FileInputStream(new File("/tmp/scastie/lanzarote.jpg")))

val small = image.scaleToWidth(200)

small.output(new File("/tmp/scastie/small.jpg"))
html"<img src='/tmp/scastie/small.jpg' alt='small lanzarote'>".fold

small.filter(SepiaFilter).output(new File("/tmp/scastie/small-sepia.jpg"))
html"<img src='/tmp/scastie/small-sepia.jpg' alt='small lanzarote sepia'>".fold
