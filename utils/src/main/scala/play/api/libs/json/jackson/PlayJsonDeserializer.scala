package play.api.libs.json.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import play.api.libs.json.{JsObject, JsonParserSettings, Reads}

class PlayJsonDeserializer[T: Reads](cls: Class[T]) extends StdDeserializer[T](cls) {
  override def deserialize(p: JsonParser, ctxt: DeserializationContext): T = {
    val der = new JsValueDeserializer(
      ctxt.getTypeFactory,
      classOf[JsObject],
      JsonParserSettings.settings
    )
    der.deserialize(p, ctxt).as[T]
  }
}
