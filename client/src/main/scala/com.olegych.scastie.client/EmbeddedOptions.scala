package com.olegych.scastie.client

import com.olegych.scastie.api.{Inputs, SnippetId, SnippetUserPart, ScalaTarget, ScalaTargetType}

import scala.scalajs.js
import scala.scalajs.js.UndefOr

trait SharedEmbeddedOptions extends js.Object {
  val serverUrl: UndefOr[String]
  val theme: UndefOr[String]

  // SnippetId
  val base64UUID: UndefOr[String]
  val user: UndefOr[String]
  val update: UndefOr[Int]
}

trait EmbeddedResourceOptionsJs extends js.Object with SharedEmbeddedOptions {
  val injectId: UndefOr[String]
}

trait EmbeddedOptionsJs extends js.Object with SharedEmbeddedOptions {
  // Inputs
  val code: UndefOr[String]
  val isWorksheetMode: UndefOr[Boolean]
  val sbtConfig: UndefOr[String]
  // val sbtPluginsConfigExtra: UndefOr[String] not yet supported

  //  target
  val targetType: UndefOr[String]
  val scalaVersion: UndefOr[String]
  // val scalaJsVersion: UndefOr[String] not yet supported
  // val scalaNativeVersion: UndefOr[String] not yet supported
}

case class EmbeddedOptions(snippetId: Option[SnippetId],
                           injectId: Option[String],
                           inputs: Option[Inputs],
                           theme: Option[String],
                           serverUrl: String) {

  def setCode(code: String): EmbeddedOptions = {
    val inputs0 = inputs.getOrElse(Inputs.default)
    copy(inputs = Some(inputs0.copy(code = code)))
  }
}

object EmbeddedOptions {
  def empty(defaultServerUrl: String): EmbeddedOptions = {
    EmbeddedOptions(
      snippetId = None,
      injectId = None,
      inputs = None,
      theme = None,
      serverUrl = defaultServerUrl
    )
  }

  private def extractSnippetId(
      options: SharedEmbeddedOptions
  ): Option[SnippetId] = {
    import options._

    base64UUID.toOption.map(
      uuid =>
        SnippetId(
          uuid,
          user.toOption
            .map(u => SnippetUserPart(u, update.toOption.getOrElse(0)))
      )
    )
  }

  def fromJsRessource(
      defaultServerUrl: String
  )(options: EmbeddedResourceOptionsJs): EmbeddedOptions = {

    import options._

    val snippetId = extractSnippetId(options)

    if (snippetId.isDefined && injectId.isEmpty) {
      sys.error(
        "injectId is not defined, we don't know where to inject the embedding"
      )
    }

    EmbeddedOptions(
      snippetId = snippetId,
      injectId = injectId.toOption,
      inputs = None,
      theme = theme.toOption,
      serverUrl = serverUrl.toOption.getOrElse(defaultServerUrl)
    )
  }

  def fromJs(
      defaultServerUrl: String
  )(options: EmbeddedOptionsJs): EmbeddedOptions = {
    import options._

    val scalaTarget =
      (targetType.toOption,
       scalaVersion.toOption,
       None: Option[String], // scalaJsVersion.toOption,
       None: Option[String] // scalaNativeVersion.toOption
      ) match {

        case (Some("jvm"), _, None, None) => {
          Some(
            scalaVersion
              .map(version => ScalaTarget.Jvm(version))
              .getOrElse(ScalaTarget.Jvm.default)
          )
        }

        case (Some("dotty" | "scala3"), _, None, None) => {
          Some(
            scalaVersion
              .map(version => ScalaTarget.Scala3(version))
              .getOrElse(ScalaTarget.Scala3.default)
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

        case (tpe, Some(scalaV), Some(jsV), None) if (tpe.contains("js") || tpe.isEmpty) => {

          Some(ScalaTarget.Js(scalaV, jsV))
        }

        case (Some("native"), None, None, None) => {
          Some(ScalaTarget.Native.default)
        }

        case (tpe, Some(scalaV), None, Some(nativeV)) if (tpe.contains("native") || tpe.isEmpty) => {
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

        val isScala3 =
          scalaTarget
            .map(_.targetType == ScalaTargetType.Scala3)
            .getOrElse(false)

        val defaultCode =
          if (isScala3) ScalaTarget.Scala3.defaultCode
          else default.code

        val inputs0 =
          default.copy(
            _isWorksheetMode = isWorksheetMode.getOrElse(default.isWorksheetMode),
            code = code.getOrElse(defaultCode),
            target = scalaTarget.getOrElse(default.target),
            sbtConfigExtra = sbtConfig.getOrElse(default.sbtConfigExtra)
          )
        Some(inputs0)
      } else {
        None
      }

    val snippetId = extractSnippetId(options)

    if (snippetId.isDefined && inputs.isDefined) {
      sys.error(
        "you cannot use both snippetId (base64UUID, user, update) & inputs (code, scalaTarget, etc)"
      )
    }

    EmbeddedOptions(
      snippetId = snippetId,
      injectId = None,
      inputs = inputs,
      theme = theme.toOption,
      serverUrl = serverUrl.toOption.getOrElse(defaultServerUrl)
    )
  }
}
