package scastie.metals

import com.olegych.scastie.api._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

object DTOCodecs {
  import JavaConverters._

  implicit val scalaTargetDecoder: Decoder[ScalaTarget] = new Decoder[ScalaTarget] {
    import com.olegych.scastie.api.ScalaTarget.ScalaTargetFormat._

    final def apply(c: HCursor): Decoder.Result[ScalaTarget] =
      val res = for {
        tpe          <- c.downField("tpe").as[String]
        scalaVersion <- c.downField("scalaVersion").as[String]
      } yield tpe -> scalaVersion

      res.flatMap((tpe, scalaVersion) =>
        tpe match {
          case "Jvm"       => Right(ScalaTarget.Jvm(scalaVersion))
          case "Js"        => c.downField("scalaJsVersion").as[String].map(ScalaTarget.Js(scalaVersion, _))
          case "Typelevel" => Right(ScalaTarget.Typelevel(scalaVersion))
          case "Native"    => c.downField("scalaNativeVersion").as[String].map(ScalaTarget.Native(scalaVersion, _))
          case "Scala3" | "Dotty" => Right(ScalaTarget.Scala3(scalaVersion))
        }
      )

  }

  implicit val scalaDependencyDecoder: Decoder[ScalaDependency] = deriveDecoder
  implicit val scalaDependencyEncoder: Encoder[ScalaDependency] = deriveEncoder

  implicit val lspRequestDecoder: Decoder[LSPRequestDTO] = deriveDecoder
  implicit val lspRequestEncoder: Encoder[LSPRequestDTO] = deriveEncoder

  implicit val completionInfoRequestDecoder: Decoder[CompletionInfoRequest] = deriveDecoder
  implicit val completionInfoRequestEncoder: Encoder[CompletionInfoRequest] = deriveEncoder

  implicit val completionItemDTOEncoder: Encoder[CompletionItemDTO] = deriveEncoder

  implicit val additionalInsertInstructionsEncoder: Encoder[AdditionalInsertInstructions] = deriveEncoder
  implicit val additionalInsertInstructionsDecoder: Decoder[AdditionalInsertInstructions] = deriveDecoder

  implicit val failureTypeEncoder: Encoder[FailureType] = new Encoder[FailureType] {
    implicit val noResultEncoder: Encoder.AsObject[NoResult] = deriveEncoder[NoResult]
    implicit val presentationCompilerFailureEncoder: Encoder.AsObject[PresentationCompilerFailure] =
      deriveEncoder[PresentationCompilerFailure]

    def apply(a: FailureType): Json = (a match
      case noResult: NoResult                     => noResult.asJsonObject
      case pcFailure: PresentationCompilerFailure => pcFailure.asJsonObject
    ).+:("_type" -> a.getClass.getCanonicalName.toString.asJson).asJson

  }

}
