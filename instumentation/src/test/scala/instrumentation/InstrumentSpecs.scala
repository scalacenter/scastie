package instrumentation

import java.nio.file._


import scala.collection.JavaConverters._

object InstrumentSpecs {

  private def slurp(path: Path): String = {
    Files.readAllLines(path).toArray.mkString("")
  }

  def main(args: Array[String]): Unit = {
    val tests = {
      val path = Paths.get("instumentation", "src", "test", "resources")
      val s = Files.newDirectoryStream(path)
      val t = s.asScala.toList
      s.close()
      t
    }

    val results = 
      tests
        .map(p => (
          p,
          slurp(p.resolve("original.scala")),
          slurp(p.resolve("instrumented.scala"))
        ))
        .map{ case (path, instrumented, original) =>
          val assertion =
            if(Instrument(original) == instrumented) "✓"
            else "✘"

          assertion + " " + path.getFileName
        }

    println(results.mkString(System.lineSeparator))
  }
}

