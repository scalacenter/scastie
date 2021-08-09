package com.olegych.scastie.util

import com.fasterxml.jackson.core.{JsonGenerator, JsonLocation, JsonParseException, JsonParser}
import com.fasterxml.jackson.databind.{DeserializationContext, SerializerProvider}
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.olegych.scastie.api.{FetchResult, FormatRequest, FormatResponse, Inputs, SnippetId, SnippetProgress, SnippetSummary}
import play.api.libs.json.{Format, Json, Reads, Writes}

import java.io.ByteArrayInputStream
import scala.reflect.ClassTag

class PlayJsonSerializer[T: Writes](cls: Class[T]) extends StdSerializer[T](cls) {
  override def serialize(value: T, gen: JsonGenerator, provider: SerializerProvider): Unit =
    gen.writeRawValue(Json.stringify(Json.toJson(value)))
}

class PlayJsonDeserializer[T: Reads](cls: Class[T]) extends StdDeserializer[T](cls) {
  override def deserialize(p: JsonParser, ctxt: DeserializationContext): T = {
    def error() = throw new JsonParseException(p, "not support")

    p.getTokenLocation match { // current token is '{'
      case JsonLocation.NA => error()

      case loc => loc.getSourceRef match {
        case s: String =>
          val begin = loc.getCharOffset.toInt
          p.skipChildren() // current token is '}'
          val end = p.getCurrentLocation.getCharOffset.toInt
          Json.parse(s.substring(begin, end)).as[T]

        case bytes: Array[Byte] =>
          val begin = loc.getByteOffset.toInt
          p.skipChildren() // current token is '}'
          val end = p.getCurrentLocation.getByteOffset.toInt
          val in = new ByteArrayInputStream(bytes, begin, end - begin)
          Json.parse(in).as[T]

        // don't need support for other cases
        // Find usage of [[com.fasterxml.jackson.core.JsonFactory._createContext]] for all cases
        case _ => error()
      }
    }
  }
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
