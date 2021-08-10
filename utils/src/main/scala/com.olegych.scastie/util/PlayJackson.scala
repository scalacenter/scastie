package com.olegych.scastie.util

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.olegych.scastie.api._
import play.api.libs.json.jackson.PlayJsonDeserializer
import play.api.libs.json.{Format, Json, Writes}
import scala.reflect.ClassTag

class PlayJsonSerializer[T: Writes](cls: Class[T]) extends StdSerializer[T](cls) {
  override def serialize(value: T, gen: JsonGenerator, provider: SerializerProvider): Unit =
    gen.writeRawValue(Json.stringify(Json.toJson(value)))
}

class PlayJackson extends SimpleModule {
  private def add[T: ClassTag: Format] = {
    val cls = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
    addSerializer(cls, new PlayJsonSerializer[T](cls))
    addDeserializer(cls, new PlayJsonDeserializer[T](cls))
  }

  add[SnippetId]
  add[FetchResult]
  add[SnippetSummary]
  add[FormatRequest]
  add[FormatResponse]
  add[SnippetProgress]
  add[Inputs]
}
