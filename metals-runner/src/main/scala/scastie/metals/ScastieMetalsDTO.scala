package scastie.metals

import scala.meta.internal.metals.CompilerOffsetParams
import java.nio.file.Files
import java.nio.file.Path
import com.olegych.scastie.api.ScalaTarget
import com.olegych.scastie.api.ScalaDependency
import com.olegych.scastie.api._
import io.circe.Encoder
import io.circe.Decoder
import io.circe.generic.semiauto._
import io.circe._
import io.circe.syntax._
import io.circe.Codec
import scala.reflect.io.AbstractFile
import java.net.URI
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.internal.util.NoSourceFile
import scala.meta.internal.metals.ScalaTarget.apply
import org.eclipse.lsp4j.CompletionList
import scala.jdk.CollectionConverters._
import cats.syntax.all._
import org.eclipse.lsp4j.CompletionItem
import scala.meta.internal.pc.CompletionItemData
import org.eclipse.lsp4j.jsonrpc.messages.{Either => JEither}
import com.google.gson.Gson
import com.google.gson.JsonElement
import org.eclipse.lsp4j.MarkupContent



object DTOExtensions {
  extension (offsetParams: ScastieOffsetParams)
    def toOffsetParams: CompilerOffsetParams = {
      val noSourceFilePath = Path.of(NoSourceFile.path).toUri
      new CompilerOffsetParams(noSourceFilePath, offsetParams.content, offsetParams.offset)
    }
}

object JavaConverters {
  extension[A, B] (either: JEither[A, B])
    def asScala = if either.isLeft then
      Left[A, B](either.getLeft)
    else
      Right[A, B](either.getRight)


  def createInsertInstructions(completion: CompletionItem) = {
    completion.getTextEdit().asScala match {
      case Left(textEdit) =>
        val move = textEdit.getNewText.indexOf("$0")
        InsertInstructions(textEdit.getNewText.replace("$0", ""), if move > 0 then move else textEdit.getNewText.length)
      case Right(insertReplace) => throw Exception("[InsertReplaceEdit] should not appear as it is not used in metals")

    }
  }

  def parseCompletionData(completionData: Object): Option[String] =
    val gson = new Gson()
    Option(completionData).map { completionData =>
      gson.fromJson(completionData.toString, classOf[CompletionItemData]).symbol
    }

  extension (completions: CompletionList)
    def toSimpleScalaList: Set[CompletionItemDTO] =
      completions.getItems().asScala.map(completion => {
        val documentation = Option(completion.getDocumentation()).map(_.asScala.map(_.toString).merge)
        CompletionItemDTO(
          completion.getLabel(),
          documentation.getOrElse(""),
          completion.getKind.toString.toLowerCase,
          completion.getSortText().toIntOption,
          createInsertInstructions(completion),
          Option(completion.getDetail()).getOrElse(""),
          parseCompletionData(completion.getData())
        )
      }).toSet

}

object DTOCodecs {
  import JavaConverters._

  implicit val scalaTargetDecoder: Decoder[ScalaTarget] = new Decoder[ScalaTarget] {
    import com.olegych.scastie.api.ScalaTarget.ScalaTargetFormat._
    final def apply(c: HCursor): Decoder.Result[ScalaTarget] =
      val res = for {
        tpe <- c.downField("tpe").as[String]
        scalaVersion <- c.downField("scalaVersion").as[String]
      } yield tpe -> scalaVersion

      res.flatMap ( (tpe, scalaVersion) => tpe match {
        case "Jvm"              => Right(ScalaTarget.Jvm(scalaVersion))
        case "Js"               => c.downField("scalaJsVersion").as[String].map(ScalaTarget.Js(scalaVersion, _))
        case "Typelevel"        => Right(ScalaTarget.Typelevel(scalaVersion))
        case "Native"           => c.downField("scalaNativeVersion").as[String].map(ScalaTarget.Native(scalaVersion, _))
        case "Scala3" | "Dotty" => Right(ScalaTarget.Scala3(scalaVersion))
      })
  }

  implicit val scalaDependencyDecoder: Decoder[ScalaDependency] = deriveDecoder
  implicit val scalaDependencyEncoder: Encoder[ScalaDependency] = deriveEncoder

  implicit val lspRequestDecoder: Decoder[LSPRequestDTO] = deriveDecoder
  implicit val lspRequestEncoder: Encoder[LSPRequestDTO] = deriveEncoder

  implicit val completionInfoRequestDecoder: Decoder[CompletionInfoRequest] = deriveDecoder
  implicit val completionInfoRequestEncoder: Encoder[CompletionInfoRequest] = deriveEncoder

  implicit val completionItemDTOEncoder: Encoder[CompletionItemDTO] = deriveEncoder
}



