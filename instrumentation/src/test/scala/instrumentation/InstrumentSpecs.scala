package instrumentation

import java.nio.file._

import scala.collection.JavaConverters._

object InstrumentSpecs {
  private val nl = System.lineSeparator

  private def slurp(path: Path): String = {
    Files.readAllLines(path).toArray.mkString(nl)
  }

  def main(args: Array[String]): Unit = {
    val tests = {
      val path = Paths.get("instrumentation", "src", "test", "resources")
      val s    = Files.newDirectoryStream(path)
      val t    = s.asScala.toList
      s.close()
      t
    }

    val results =
      tests
        .map(
          p =>
            (
              p,
              slurp(p.resolve("original.scala")),
              slurp(p.resolve("instrumented.scala"))
          ))
        .map {
          case (path, original, instrumented) =>
            val out = Instrument(original)

            if (out == instrumented) "✓"
            else {
              def indent(in: String) =
                in.split(nl).map(s => "  " + s).mkString(nl)
              // val original2 = original
              s"""|✘ ${path.getFileName}
                  |${indent(instrumented)}
                  |*********************
                  |${indent(out)}""".stripMargin
              // "fail"
            }
        }

    println(results.mkString(System.lineSeparator))
  }
}
