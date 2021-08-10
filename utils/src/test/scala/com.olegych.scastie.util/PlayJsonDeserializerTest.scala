package com.olegych.scastie.util

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.olegych.scastie.api.{Project, ScalaTarget, ShortInputs}
import com.olegych.scastie.util.PlayJacksonTestBase._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers

class PlayJsonDeserializerTest extends AnyFunSuite with Matchers {
  test("plain jackson/ simple case class !fail!") {
    assertThrows[InvalidDefinitionException] {
      mapper().readValue(jsonInputs, classOf[ShortInputs])
    }
  }

  test("DefaultScalaModule/ simple case class") {
    mapper(DefaultScalaModule)
      .readValue(jsonProject, classOf[Project]) mustBe project
  }

  test("DefaultScalaModule/ complex class !fail!") {
    assertThrows[InvalidDefinitionException] {
      mapper(DefaultScalaModule)
        .readValue(jsonInputs, classOf[ShortInputs])
    }
  }

  test("PlayJson/ simple case class") {
    mapper(deserializerModule[Project])
      .readValue(jsonProject, classOf[Project]) mustBe project
  }

  test("PlayJson/ complex class success. Don't need Serializer for inner field type (ScalaTarget)") {
    mapper(deserializerModule[ShortInputs])
      .readValue(jsonInputs, classOf[ShortInputs]) mustBe inputs
  }

  test("PlayJson for one field (ScalaTarget) & DefaultScalaModule for outer object (ShortInputs)") {
    mapper(deserializerModule[ScalaTarget], DefaultScalaModule)
      .readValue(jsonInputs, classOf[ShortInputs]) mustBe inputs
  }
}
