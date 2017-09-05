package com.olegych.scastie.client

import com.olegych.scastie.api.{
  Inputs,
  SnippetId,
  SnippetUserPart,
  ScalaTarget,
  ScalaTargetType
}

import scala.scalajs.js
import scala.scalajs.js.annotation.ScalaJSDefined
import scala.scalajs.js.UndefOr

@ScalaJSDefined
trait EmbeddedOptionsJs extends js.Object {
  // General
  val serverUrl: UndefOr[String]

  // SnippetId
  val base64UUID: UndefOr[String]
  val user: UndefOr[String]
  val update: UndefOr[Int]

  // Inputs
  val code: UndefOr[String]
  val worksheetMode: UndefOr[Boolean]
  val sbtConfig: UndefOr[String]
  // val sbtPluginsConfigExtra: UndefOr[String] not yet supported

  //  target
  val targetType: UndefOr[String]
  val scalaVersion: UndefOr[String]
  val scalaJsVersion: UndefOr[String]
  val scalaNativeVersion: UndefOr[String]
}

object EmbeddedOptions {
  def empty: EmbeddedOptions = EmbeddedOptions(None, None, None)

  def fromJs(options: EmbeddedOptionsJs): EmbeddedOptions = {
    import options._

    val scalaTarget =
      (targetType.toOption,
       scalaVersion.toOption,
       scalaJsVersion.toOption,
       scalaNativeVersion.toOption) match {

        case (Some("jvm"), _, None, None) => {
          Some(
            scalaVersion
              .map(version => ScalaTarget.Jvm(version))
              .getOrElse(ScalaTarget.Jvm.default)
          )
        }

        case (Some("dotty"), _, None, None) => {
          Some(
            scalaVersion
              .map(version => ScalaTarget.Dotty(version))
              .getOrElse(ScalaTarget.Dotty.default)
          )
        }

        case (Some("typelevel"), _, None, None) => {
          Some(
            scalaVersion
              .map(version => ScalaTarget.Typelevel(version))
              .getOrElse(ScalaTarget.Typelevel.default)
          )
        }

        case (Some("js"), None, None, None) => {
          Some(ScalaTarget.Js.default)
        }

        case (tpe, Some(scalaV), Some(jsV), None)
            if (tpe.contains("js") || tpe.isEmpty) => {

          Some(ScalaTarget.Js(scalaV, jsV))
        }

        case (Some("native"), None, None, None) => {
          Some(ScalaTarget.Native.default)
        }

        case (tpe, Some(scalaV), None, Some(nativeV))
            if (tpe.contains("native") || tpe.isEmpty) => {
          Some(ScalaTarget.Native(scalaV, nativeV))
        }

        case (None, None, None, None) => None

        case (a, b, c, d) => {
          sys.error(
            s"invalid scala target combination: $a | $b | $c | $d"
          )
        }
      }

    val inputs =
      if (scalaTarget.isDefined || code.isDefined) {
        val default = Inputs.default

        val isDotty =
          scalaTarget
            .map(_.targetType == ScalaTargetType.Dotty)
            .getOrElse(false)

        val defaultCode =
          if (isDotty) ScalaTarget.Dotty.defaultCode
          else default.code

        val inputs0 =
          default.copy(
            worksheetMode = worksheetMode.getOrElse(default.worksheetMode),
            code = code.getOrElse(defaultCode),
            target = scalaTarget.getOrElse(default.target),
            sbtConfigExtra = sbtConfig.getOrElse(default.sbtConfigExtra)
          )
        Some(inputs0)
      } else {
        None
      }

    val snippetId =
      base64UUID.toOption.map(
        uuid =>
          SnippetId(
            uuid,
            user.toOption
              .map(u => SnippetUserPart(u, update.toOption.getOrElse(0)))
        )
      )

    if (snippetId.isDefined && inputs.isDefined) {
      sys.error("You cannot define both snippetId & inputs")
    }

    EmbeddedOptions(
      inputs = inputs,
      snippetId = snippetId,
      serverUrl = serverUrl.toOption
    )
  }
}

case class EmbeddedOptions(snippetId: Option[SnippetId],
                           inputs: Option[Inputs],
                           serverUrl: Option[String]) {

  def setCode(code: String): EmbeddedOptions = {
    val inputs0 = inputs.getOrElse(Inputs.default)
    copy(inputs = Some(inputs0.copy(code = code)))
  }
}
