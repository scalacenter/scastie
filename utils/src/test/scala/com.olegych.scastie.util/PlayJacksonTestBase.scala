package com.olegych.scastie.util

import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.olegych.scastie.api.{BaseInputs, Project, ScalaTarget, ShortInputs}
import play.api.libs.json.{Reads, Writes}

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
}
