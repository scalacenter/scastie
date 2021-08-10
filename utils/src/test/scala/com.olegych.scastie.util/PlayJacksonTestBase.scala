package com.olegych.scastie.util

import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.olegych.scastie.api.{BaseInputs, Project, ScalaTarget, ShortInputs}
import play.api.libs.json.jackson.PlayJsonDeserializer
import play.api.libs.json.{Format, Json, Reads, Writes}

import scala.reflect.ClassTag

object PlayJacksonTestBase {
  def mapper(modules: Module*): JsonMapper =
    modules.foldLeft(JsonMapper.builder()){
      (b, m) => b.addModule(m)
    }.build()

  def serializerModule[T: ClassTag: Writes]: SimpleModule = {
    val cls = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
    new SimpleModule().addSerializer(new PlayJsonSerializer(cls))
  }

  def deserializerModule[T: ClassTag: Reads]: SimpleModule = {
    val cls = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
    new SimpleModule().addDeserializer(cls, new PlayJsonDeserializer(cls))
  }

  val inputs: BaseInputs = ShortInputs("code", ScalaTarget.Scala3("3.0.1"))
  val jsonInputs = """{"code":"code","target":{"dottyVersion":"3.0.1","tpe":"Scala3"}}"""

  val project: Project = Project("org", "repo", Some("logo"), List("art1"))
  val jsonProject = """{"organization":"org","repository":"repo","logo":"logo","artifacts":["art1"]}"""

  /** class that has other field after `target`
   * This is used to verify that when deserializing the following input source:{{{
   * {"target":{...},"code":"some code"}
   *               ^
   * }}}
   * using PlayJsonDeserializer for ScalaTarget and other deserializer for Input2 and other fields
   * then PlayJsonDeserializer do NOT consume the input source beyond the ending '}' location
   * for `target` field
   */
  case class Input2(target: ScalaTarget, code: String)
  object Input2 {
    implicit val format: Format[Input2] = Json.format[Input2]
  }
  val input2:Input2 = Input2(ScalaTarget.Scala3("3.0.1"), "code")
  val jsonInput2 = """{"target":{"dottyVersion":"3.0.1","tpe":"Scala3"},"code":"code"}"""
}
