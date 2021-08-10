package com.olegych.scastie.util

import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.olegych.scastie.api.{Project, ScalaTarget, ShortInputs}
import com.olegych.scastie.util.PlayJacksonTestBase._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers

class PlayJsonSerializerTest extends AnyFunSuite with Matchers {
  test("plain jackson/ simple case class !fail!") {
    val ret = mapper().writeValueAsString(inputs)
    ret must not be jsonInputs
    ret mustBe "{}"
  }

  test("DefaultScalaModule/ simple case class") {
    mapper(DefaultScalaModule).writeValueAsString(project) mustBe jsonProject
  }

  test("DefaultScalaModule/ complex class !fail!") {
    val ret = mapper().writeValueAsString(inputs)
    ret must not be jsonInputs
    ret mustBe "{}"
  }

  test("PlayJson/ simple case class") {
    mapper(serializerModule[Project])
      .writeValueAsString(project) mustBe jsonProject
  }

  test("PlayJson/ complex class success. Don't need Serializer for inner field type (ScalaTarget)") {
    mapper(serializerModule[ShortInputs])
      .writeValueAsString(inputs) mustBe jsonInputs
  }

  test("PlayJson for one field (ScalaTarget) & DefaultScalaModule for outer object (ShortInputs)") {
    mapper(serializerModule[ScalaTarget], DefaultScalaModule)
      .writeValueAsString(inputs) mustBe jsonInputs
  }
}
