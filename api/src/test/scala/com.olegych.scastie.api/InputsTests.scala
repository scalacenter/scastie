package com.olegych.scastie.api

import org.scalatest.FunSuite

class InputsTests extends FunSuite {

  test("sbtConfig default") {

    val obtained = InputsHelper.sbtConfig(InputsHelper.default)

    val expected =
      """|scalaVersion := "2.12.3"
         |
         |libraryDependencies += "org.scastie" %% "runtime-scala" % "0.25.0-SNAPSHOT"
         |
         |scalacOptions ++= Seq(
         |  "-deprecation",
         |  "-encoding", "UTF-8",
         |  "-feature",
         |  "-unchecked"
         |)
         |
         |ensimeIgnoreMissingDirectories := true""".stripMargin

    assert(obtained == expected)
  }
}