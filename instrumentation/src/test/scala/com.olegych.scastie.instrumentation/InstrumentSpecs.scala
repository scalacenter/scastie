package com.olegych.scastie
package instrumentation

import java.nio.file._

import com.olegych.scastie.api.ScalaTarget
import com.olegych.scastie.util.ScastieFileUtil.slurp
import org.scalatest.funsuite.AnyFunSuite

import scala.jdk.CollectionConverters._

class InstrumentSpecs extends AnyFunSuite {

  import InstrumentationFailure._

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

      val target =
        if (dirName == "scalajs") ScalaTarget.Js.default
        else ScalaTarget.Jvm.default

      val Right(obtained) = Instrument(original, target)

      Diff.assertNoDiff(obtained.trim, expected.trim)
    }
  }

  test("top level fails") {
    val Left(e) = Instrument("package foo { }")
    assert(e.isInstanceOf[ParsingError])
  }

  test("main method fails") {
    val Left(HasMainMethod) =
      Instrument("object Main { def main(args: Array[String]): Unit = () }")
  }

  test("extends App trait fails") {
    val Left(HasMainMethod) =
      Instrument("object Main extends App { }")
  }

  test("with App trait fails") {
    val Left(HasMainMethod) =
      Instrument("trait Foo; object Main extends Foo with App { }")
  }

  test("extends App primary fails") {
    val Left(HasMainMethod) = Instrument("object Main extends App")
  }

  test("extends App secondary fails") {
    val Left(HasMainMethod) = Instrument("object Main extends A with App")
  }

  test("bug #83") {
    val Right(_) = Instrument("val answer: 42 = 42", ScalaTarget.Dotty.default)
  }
}
