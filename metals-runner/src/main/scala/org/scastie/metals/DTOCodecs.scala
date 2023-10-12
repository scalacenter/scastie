package org.scastie.metals

// import org.scastie.api._
// import io.circe._
// import io.circe.generic.semiauto._
// import io.circe.syntax._
// import org.scastie.api.ScalaTarget._

// object DTOCodecs {
//   import JavaConverters._

//   implicit val scalaTargetDecoder: Decoder[ScalaTarget] = new Decoder[ScalaTarget] {
//     import org.scastie.api.ScalaTarget.ScalaTargetFormat._

//     final def apply(c: HCursor): Decoder.Result[ScalaTarget] =
//       val res = for {
//         tpe          <- c.downField("tpe").as[String]
//         scalaVersion <- if (tpe == "ScalaCli") then Right("") else c.downField("scalaVersion").as[String]
//       } yield tpe -> scalaVersion

//       res.flatMap((tpe, scalaVersion) =>
//         tpe match {
//           case "Jvm"       => Right(ScalaTarget.Jvm(scalaVersion))
//           case "Js"        => c.downField("scalaJsVersion").as[String].map(ScalaTarget.Js(scalaVersion, _))
//           case "Typelevel" => Right(ScalaTarget.Typelevel(scalaVersion))
//           case "Native"    => c.downField("scalaNativeVersion").as[String].map(ScalaTarget.Native(scalaVersion, _))
//           case "Scala3" | "Dotty" => Right(ScalaTarget.Scala3(scalaVersion))
//           case "ScalaCli"  => Right(ScalaTarget.ScalaCli())
//         }
//       )
//   }

//   implicit val scalaTargetEncoder: Encoder[ScalaTarget] = new Encoder[ScalaTarget] {
//     def apply(a: ScalaTarget): Json = {
//       val supplementaryFields = a match
//           case Jvm(scalaVersion) => List("tpe" -> "Jvm")
//           case Typelevel(scalaVersion) => List("tpe" -> "Typelevel")
//           case Js(scalaVersion, scalaJsVersion) => List("tpe" -> "Js", "scalaJsVersion" -> scalaJsVersion)
//           case Native(scalaVersion, scalaNativeVersion) => List("tpe" -> "Native", "scalaNativeVersion" -> scalaNativeVersion)
//           case Scala3(scalaVersion) => List("tpe" -> "Scala3")
//           case ScalaCli() => List("tpe" -> "ScalaCli")

//       Json.fromFields(
//         (List("scalaVersion" -> a.scalaVersion) ++ supplementaryFields).map({
//           case (a1, a2) => (a1, Json.fromString(a2))
//         })
//       )
//     }
//   }

//   implicit val scalaDependencyDecoder: Decoder[ScalaDependency] = deriveDecoder
//   implicit val scalaDependencyEncoder: Encoder[ScalaDependency] = deriveEncoder

//   implicit val lspRequestDecoder: Decoder[LSPRequestDTO] = deriveDecoder
//   implicit val lspRequestEncoder: Encoder[LSPRequestDTO] = deriveEncoder

//   implicit val completionInfoRequestDecoder: Decoder[CompletionInfoRequest] = deriveDecoder
//   implicit val completionInfoRequestEncoder: Encoder[CompletionInfoRequest] = deriveEncoder

//   implicit val completionItemDTOEncoder: Encoder[CompletionItemDTO] = deriveEncoder

//   implicit val additionalInsertInstructionsEncoder: Encoder[AdditionalInsertInstructions] = deriveEncoder
//   implicit val additionalInsertInstructionsDecoder: Decoder[AdditionalInsertInstructions] = deriveDecoder

//   implicit val failureTypeEncoder: Encoder[FailureType] = new Encoder[FailureType] {
//     implicit val noResultEncoder: Encoder.AsObject[NoResult] = deriveEncoder[NoResult]
//     implicit val presentationCompilerFailureEncoder: Encoder.AsObject[PresentationCompilerFailure] =
//       deriveEncoder[PresentationCompilerFailure]
//     implicit val invalidScalaVersionEncoder: Encoder.AsObject[InvalidScalaVersion] =
//       deriveEncoder[InvalidScalaVersion]

//     def apply(a: FailureType): Json = (a match
//       case noResult: NoResult                     => noResult.asJsonObject
//       case pcFailure: PresentationCompilerFailure => pcFailure.asJsonObject
//       case invScalaVersion: InvalidScalaVersion   => invScalaVersion.asJsonObject
//     ).+:("_type" -> a.getClass.getCanonicalName.toString.asJson).asJson

//   }

// }
