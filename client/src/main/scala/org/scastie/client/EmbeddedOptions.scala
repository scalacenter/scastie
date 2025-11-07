package org.scastie.client

import org.scastie.api._

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
                           inputs: Option[BaseInputs],
                           theme: Option[String],
                           serverUrl: String) {

  def setCode(code: String): EmbeddedOptions = {
    val inputs0: BaseInputs = inputs.getOrElse(ScalaCliInputs.default)
    copy(inputs = Some(inputs0.copyBaseInput(code = code)))
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
              .map(version => Scala2(version))
              .getOrElse(Scala2.default)
          )
        }

        case (Some("dotty" | "scala3"), _, None, None) => {
          Some(
            scalaVersion
              .map(version => Scala3(version))
              .getOrElse(Scala3.default)
          )
        }

        case (Some("typelevel"), _, None, None) => {
          Some(
            scalaVersion
              .map(version => Typelevel(version))
              .getOrElse(Typelevel.default)
          )
        }

        case (Some("scala-cli"), _, None, None) => {
          Some(
            scalaVersion
              .map(version => ScalaCli(version))
              .getOrElse(ScalaCli.default)
          )
        }

        case (Some("js"), None, None, None) => {
          Some(Js.default)
        }

        case (tpe, Some(scalaV), Some(jsV), None) if (tpe.contains("js") || tpe.isEmpty) => {

          Some(Js(scalaV, jsV))
        }

        case (Some("native"), None, None, None) => {
          Some(Native.default)
        }

        case (tpe, Some(scalaV), None, Some(nativeV)) if (tpe.contains("native") || tpe.isEmpty) => {
          Some(Native(scalaV, nativeV))
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

        val sbtConfigExtraOption = sbtConfig.toOption

        val default: BaseInputs =
          scalaTarget match {
            case Some(target: ScalaCli) => 
              ScalaCliInputs.default.copy(target = target)
            case Some(target: SbtScalaTarget) =>
              SbtInputs.default.copy(target = target, sbtConfigExtra = sbtConfigExtraOption.getOrElse(SbtInputs.default.sbtConfigExtra))
            case None => ScalaCliInputs.default
          }

        val defaultCode = 
          scalaTarget match {
            case Some(_: ScalaCli) => ScalaCli.defaultCode
            case Some(_: Scala3) => Scala3.defaultCode
            case _ => default.code
          }

        val inputs0 =
          default.copyBaseInput(
            isWorksheetMode = isWorksheetMode.getOrElse(default.isWorksheetMode),
            code = code.getOrElse(defaultCode),
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
