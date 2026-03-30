package org.scastie.sbt

import org.scalatest.funsuite.AnyFunSuite
import org.scastie.api.{BaseInputs, SbtInputs}

class SbtScalacOptionsTest extends AnyFunSuite {

  test("filterPcRelevant keeps -language: flags") {
    val input = List("-language:experimental.captureChecking", "-deprecation")
    assert(BaseInputs.filterPcRelevant(input) == List("-language:experimental.captureChecking"))
  }

  test("filterPcRelevant keeps -source: flags") {
    val input = List("-source:future", "-feature")
    assert(BaseInputs.filterPcRelevant(input) == List("-source:future"))
  }

  test("filterPcRelevant keeps -X and -Y flags") {
    val input = List("-Xfatal-warnings", "-Yexplicit-nulls", "-unchecked")
    assert(BaseInputs.filterPcRelevant(input) == List("-Xfatal-warnings", "-Yexplicit-nulls"))
  }

  test("filterPcRelevant returns empty for only standard flags") {
    val input = List("-deprecation", "-encoding", "UTF-8", "-feature", "-unchecked")
    assert(BaseInputs.filterPcRelevant(input) == Nil)
  }

  test("extracts scalacOptions from += syntax") {
    val config = """scalacOptions += "-language:experimental.captureChecking""""
    assert(SbtInputs.extractPcScalacOptions(config) == List("-language:experimental.captureChecking"))
  }

  test("extracts scalacOptions from ++= Seq syntax") {
    val config = """scalacOptions ++= Seq("-deprecation", "-language:experimental.captureChecking", "-feature")"""
    assert(SbtInputs.extractPcScalacOptions(config) == List("-language:experimental.captureChecking"))
  }

  test("extracts from both += and ++= in same config") {
    val config =
      """scalacOptions += "-Xfatal-warnings"
        |scalacOptions ++= Seq("-deprecation", "-source:future")""".stripMargin
    assert(SbtInputs.extractPcScalacOptions(config) == List("-Xfatal-warnings", "-source:future"))
  }

  test("default scastie config returns empty") {
    val config = """|scalacOptions ++= Seq(
                    |  "-deprecation",
                    |  "-encoding", "UTF-8",
                    |  "-feature",
                    |  "-unchecked"
                    |)""".stripMargin
    assert(SbtInputs.extractPcScalacOptions(config) == Nil)
  }

  test("empty config returns empty") {
    assert(SbtInputs.extractPcScalacOptions("") == Nil)
  }
}
