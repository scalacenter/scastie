package play.api.libs.json.jackson

import com.fasterxml.jackson.core.{JsonParser, JsonTokenId}
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import play.api.libs.json._
import JsonParserSettings.{settings => parserSettings}
import scala.annotation.{switch, tailrec}
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

class PlayJsonDeserializer[T: Reads](cls: Class[T]) extends StdDeserializer[T](cls) {
  override def deserialize(p: JsonParser, ctxt: DeserializationContext): T =
    deserialize(p, ctxt, List()).as[T]

  // copy from play-json 2.10.0-RC5 / play.api.libs.json.jackson.JsValueDeserializer.parseBigDecimal
  private def parseBigDecimal(
      jp: JsonParser,
      parserContext: List[DeserializerContext]
  ): (Some[JsNumber], List[DeserializerContext]) = {
    BigDecimalParser.parse(jp.getText, parserSettings) match {
      case JsSuccess(bigDecimal, _) =>
        (Some(JsNumber(bigDecimal)), parserContext)

      case JsError((_, JsonValidationError("error.expected.numberdigitlimit" +: _) +: _) +: _) =>
        throw new IllegalArgumentException(s"Number is larger than supported for field '${jp.currentName}'")

      case JsError((_, JsonValidationError("error.expected.numberscalelimit" +: _, args @ _*) +: _) +: _) =>
        val scale = args.headOption.fold("")(scale => s" ($scale)")
        throw new IllegalArgumentException(s"Number scale$scale is out of limits for field '${jp.currentName}'")

      case JsError((_, JsonValidationError("error.expected.numberformatexception" +: _) +: _) +: _) =>
        throw new NumberFormatException

      case JsError(errors) =>
        throw JsResultException(errors)
    }
  }

  // copy from play-json 2.10.0-RC5 / play.api.libs.json.jackson.JsValueDeserializer.parseBigDecimal
  @tailrec
  final def deserialize(
      jp: JsonParser,
      ctxt: DeserializationContext,
      parserContext: List[DeserializerContext]
  ): JsValue = {
    if (jp.getCurrentToken == null) {
      jp.nextToken() // happens when using treeToValue (we're not parsing tokens)
    }

    val valueAndCtx = (jp.getCurrentToken.id(): @switch) match {
      case JsonTokenId.ID_NUMBER_INT | JsonTokenId.ID_NUMBER_FLOAT => parseBigDecimal(jp, parserContext)

      case JsonTokenId.ID_STRING => (Some(JsString(jp.getText)), parserContext)

      case JsonTokenId.ID_TRUE => (Some(JsBoolean(true)), parserContext)

      case JsonTokenId.ID_FALSE => (Some(JsBoolean(false)), parserContext)

      case JsonTokenId.ID_NULL => (Some(JsNull), parserContext)

      case JsonTokenId.ID_START_ARRAY => (None, ReadingList(ArrayBuffer()) +: parserContext)

      case JsonTokenId.ID_END_ARRAY =>
        parserContext match {
          case ReadingList(content) :: stack => (Some(JsArray(content)), stack)
          case _                             => throw new RuntimeException("We should have been reading list, something got wrong")
        }

      case JsonTokenId.ID_START_OBJECT => (None, ReadingMap(ListBuffer()) +: parserContext)

      case JsonTokenId.ID_FIELD_NAME =>
        parserContext match {
          case (c: ReadingMap) :: stack => (None, c.setField(jp.getCurrentName) +: stack)
          case _                        => throw new RuntimeException("We should be reading map, something got wrong")
        }

      case JsonTokenId.ID_END_OBJECT =>
        parserContext match {
          case ReadingMap(content) :: stack => (Some(JsObject(content)), stack)
          case _                            => throw new RuntimeException("We should have been reading an object, something got wrong")
        }

      case JsonTokenId.ID_NOT_AVAILABLE =>
        throw new RuntimeException("We should have been reading an object, something got wrong")

      case JsonTokenId.ID_EMBEDDED_OBJECT =>
        throw new RuntimeException("We should have been reading an object, something got wrong")
    }

    // Read ahead
    jp.nextToken()

    valueAndCtx match {
      case (Some(v), Nil)               => v // done, no more tokens and got a value!
      case (Some(v), previous :: stack) => deserialize(jp, ctxt, previous.addValue(v) :: stack)
      case (None, nextContext)          => deserialize(jp, ctxt, nextContext)
    }
  }
}
