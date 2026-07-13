package org.scastie.scalacli

import org.scalatest.funsuite.AnyFunSuite
import org.scastie.api.ScalaCliInputs

class ScalaCliScalacOptionsTest extends AnyFunSuite {

  test("extracts single option directive") {
    val directives = List("//> using option -language:experimental.captureChecking")
    assert(ScalaCliInputs.extractPcScalacOptions(directives) == List("-language:experimental.captureChecking"))
  }

  test("extracts multiple options from single directive") {
    val directives = List("//> using options -Xfatal-warnings -Yexplicit-nulls")
    assert(ScalaCliInputs.extractPcScalacOptions(directives) == List("-Xfatal-warnings", "-Yexplicit-nulls"))
  }

  test("extracts from multiple directives") {
    val directives = List(
      "//> using option -language:experimental.captureChecking",
      "//> using options -source:future -Xfatal-warnings"
    )
    assert(ScalaCliInputs.extractPcScalacOptions(directives) == List(
      "-language:experimental.captureChecking", "-source:future", "-Xfatal-warnings"
    ))
  }

  test("filters out non-PC-relevant flags") {
    val directives = List("//> using options -deprecation -feature -language:experimental.captureChecking")
    assert(ScalaCliInputs.extractPcScalacOptions(directives) == List("-language:experimental.captureChecking"))
  }

  test("returns empty for no option directives") {
    val directives = List("//> using scala 3.8.2", "//> using dep com.lihaoyi::os-lib:0.9.1")
    assert(ScalaCliInputs.extractPcScalacOptions(directives) == Nil)
  }

  test("returns empty for empty input") {
    assert(ScalaCliInputs.extractPcScalacOptions(Nil) == Nil)
  }

  test("handles quoted option values") {
    val directives = List("""//> using option "-language:experimental.captureChecking"""")
    assert(ScalaCliInputs.extractPcScalacOptions(directives) == List("-language:experimental.captureChecking"))
  }

  test("handles quoted multiple options") {
    val directives = List("""//> using options "-Xfatal-warnings" "-Yexplicit-nulls"""")
    assert(ScalaCliInputs.extractPcScalacOptions(directives) == List("-Xfatal-warnings", "-Yexplicit-nulls"))
  }
}
