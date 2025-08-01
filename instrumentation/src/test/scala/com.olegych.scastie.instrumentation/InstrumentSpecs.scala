package org.scastie
package instrumentation

import org.scastie.api.ScalaTarget
import org.scastie.util.ScastieFileUtil.slurp
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file._
import scala.jdk.CollectionConverters._

class InstrumentSpecs extends AnyFunSuite {

  import InstrumentationFailure._

  private val testFiles = {
    val path = Paths.get("instrumentation", "src", "test", "resources")
    val s = Files.newDirectoryStream(path)
    val t = s.asScala.toList.filter(_.endsWith(".scala"))
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
        else if (dirName == "scala3") ScalaTarget.Scala3.default
        else ScalaTarget.Jvm.default

      val Right(obtained) = Instrument(original, target)

      Files.write(path.resolve("obtained.scala"), obtained.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      Diff.assertNoDiff(obtained.trim, expected.trim)
    }
  }

  test("top level fails") {
    val Left(e) = Instrument("package foo { }", ScalaTarget.Jvm.default)
    assert(e.isInstanceOf[ParsingError])
  }

  test("main method fails") {
    val Left(HasMainMethod) =
      Instrument("object Main { def main(args: Array[String]): Unit = () }", ScalaTarget.Jvm.default)
  }

  test("extends App trait fails") {
    val Left(HasMainMethod) =
      Instrument("object Main extends App { }", ScalaTarget.Jvm.default)
  }

  test("with App trait fails") {
    val Left(HasMainMethod) =
      Instrument("trait Foo; object Main extends Foo with App { }", ScalaTarget.Jvm.default)
  }

  test("extends App primary fails") {
    val Left(HasMainMethod) = Instrument("object Main extends App", ScalaTarget.Jvm.default)
  }

  test("extends App secondary fails") {
    val Left(HasMainMethod) = Instrument("object Main extends A with App", ScalaTarget.Jvm.default)
  }

  test("bug #83") {
    val Right(_) = Instrument("val answer: 42 = 42", ScalaTarget.Scala3.default)
  }
}
