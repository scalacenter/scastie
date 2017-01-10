package instrumentation

import java.nio.file._

import scala.collection.JavaConverters._

import System.{lineSeparator => nl}

import utest._

object InstrumentSpecs extends TestSuite {
  private val testFiles = {
    val path = Paths.get("instrumentation", "src", "test", "resources")
    val s    = Files.newDirectoryStream(path)
    val t    = s.asScala.toList
    s.close()
    t
  }

  val tests = this {
    'instrumentation {
      val results =
        tests.map { path =>
          val original = slurp(p.resolve("original.scala"))
          val expected = slurp(p.resolve("instrumented.scala"))

          val obtained = Instrument(original)

          Diff.assertNoDiff(obtained, expected)
        }

      println(results.mkString(System.lineSeparator))
    }
  }

  private def slurp(path: Path): String = {
    Files.readAllLines(path).toArray.mkString(nl)
  }

  def main(args: Array[String]): Unit = {}
}
