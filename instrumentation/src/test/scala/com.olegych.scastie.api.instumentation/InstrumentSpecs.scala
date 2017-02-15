package com.olegych.scastie.instrumentation

import java.nio.file._

import scala.collection.JavaConverters._

import System.{lineSeparator => nl}

import org.scalatest.FunSuite

class InstrumentSpecs extends FunSuite {

  private val testFiles = {
    val path = Paths.get("instrumentation", "src", "test", "resources")
    val s = Files.newDirectoryStream(path)
    val t = s.asScala.toList
    s.close()
    t
  }

  testFiles.foreach { path =>
    test(path.toString) {
      val original = slurp(path.resolve("original.scala"))
      val expected = slurp(path.resolve("instrumented.scala"))

      val Right(obtained) = Instrument(original)

      Diff.assertNoDiff(obtained, expected)
    }
  }

  test("top level fails"){
    val Left(()) = Instrument("package foo { }")
  }

  test("main method fails"){
    val Left(()) = Instrument("object Main { def main(args: Array[String]): Unit = () }")
  }

  test("extends App primary fails"){
    val Left(()) = Instrument("object Main extends App") 
  }

  test("extends App secondary fails"){
    val Left(()) = Instrument("object Main extends A with App") 
  }

  private def slurp(path: Path): String = {
    Files.readAllLines(path).toArray.mkString(nl)
  }
}
