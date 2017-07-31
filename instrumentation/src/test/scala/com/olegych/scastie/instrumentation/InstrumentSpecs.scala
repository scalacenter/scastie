package com.olegych.scastie.instrumentation

import java.nio.file._

import com.olegych.scastie.api._
import com.olegych.scastie.proto
import com.olegych.scastie.proto.ScalaTarget
import com.olegych.scastie.util.ScastieFileUtil.slurp
import org.scalatest.FunSuite

import scala.collection.JavaConverters._

class InstrumentSpecs extends FunSuite {

  private val testFiles = {
    val path = Paths.get("instrumentation", "src", "test", "resources")
    val s = Files.newDirectoryStream(path)
    val t = s.asScala.toList
    s.close()
    t
  }

  testFiles.foreach { path =>
    val dirName = path.getFileName.toString

    test(dirName) {
      val original = slurp(path.resolve("original.scala")).get
      val expected = slurp(path.resolve("instrumented.scala")).get

      val target: proto.ScalaTarget =
        if (dirName == "scalajs") ScalaJs.default
        else PlainScala.default

      val Right(obtained) = Instrument(original, target)

      Diff.assertNoDiff(obtained, expected)
    }
  }

  test("top level fails") {
    val Left(e) = Instrument("package foo { }", PlainScala.default)
    assert(e.isInstanceOf[ParsingError])
  }

  test("main method fails") {
    val Left(HasMainMethod) =
      Instrument(
        "object Main { def main(args: Array[String]): Unit = () }",
        PlainScala.default
      )
  }

  test("extends App trait fails") {
    val Left(HasMainMethod) =
      Instrument(
        "object Main extends App { }",
        PlainScala.default
      )
  }

  test("with App trait fails") {
    val Left(HasMainMethod) =
      Instrument(
        "trait Foo; object Main extends Foo with App { }",
        PlainScala.default
      )
  }

  test("unsupported dialect") {
    val Left(UnsupportedDialect) =
      Instrument("1", PlainScala("2.13.0"))
  }

  test("extends App primary fails") {
    val Left(HasMainMethod) = Instrument(
      "object Main extends App",
      PlainScala.default
    )
  }

  test("extends App secondary fails") {
    val Left(HasMainMethod) = Instrument(
      "object Main extends A with App",
      PlainScala.default
    )
  }

  test("bug #83") {
    val Right(_) = Instrument(
      "val answer: 42 = 42",
      Dotty.default
    )
  }
}
