package instrumentation

import java.nio.file._

import scala.collection.JavaConverters._

import System.{lineSeparator => nl}

import org.scalatest.FunSuite

class InstrumentSpecs extends FunSuite {

  private val testFiles = {
    val path = Paths.get("instrumentation", "src", "test", "resources")
    val s    = Files.newDirectoryStream(path)
    val t    = s.asScala.toList
    s.close()
    t
  }

  testFiles.foreach { path =>
    test(path.toString) {
      val original = slurp(path.resolve("original.scala"))
      val expected = slurp(path.resolve("instrumented.scala"))

      val obtained = Instrument(original)

      Diff.assertNoDiff(obtained, expected)
    }
  }

  private def slurp(path: Path): String = {
    Files.readAllLines(path).toArray.mkString(nl)
  }
}
