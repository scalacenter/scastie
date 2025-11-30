package org.scastie.client
package components

import org.scastie.api._
import japgolly.scalajs.react._
import vdom.all._
import org.scalajs.dom
import scala.scalajs.js
import org.scastie.client.i18n.I18n

final case class DownloadButton(snippetId: SnippetId, scalaTarget: ScalaTarget, language: String) {
  @inline def render: VdomElement = DownloadButton.component(this)
}

object DownloadButton {
  implicit val reusability: Reusability[DownloadButton] =
    Reusability.derive[DownloadButton]

  /** Produce a filesystem-friendly base name from a SnippetId.
    *
    * Replace non-alphanumeric (except dot and hyphen) with hyphens,
    * collapse multiple hyphens and trim leading/trailing hyphens.
    *
    * Adjust `snippetId.toString` if your SnippetId has a different accessor,
    * e.g. `snippetId.value` — change that here to match the type.
    */
  private def filesystemFriendlyName(snippetId: SnippetId): String = {
    val raw = snippetId.toString
    raw.replaceAll("[^A-Za-z0-9\\.\\-]+", "-")
       .replaceAll("-{2,}", "-")
       .replaceAll("(^-+)|(-+$)", "")
  }

  /** Server-based download URL helper. */
  private def downloadUrl(snippetId: SnippetId, language: String): String =
    s"/api/download/${snippetId.toString}/${language}"

  def render(props: DownloadButton): VdomElement = {
    val isScalaCliTarget = props.scalaTarget.targetType == ScalaTargetType.ScalaCli
    val filenameBase = filesystemFriendlyName(props.snippetId)
    val downloadFilename = s"$filenameBase.zip"
    val fullUrl = downloadUrl(props.snippetId, props.language)
    val hrefAttr = if (isScalaCliTarget) "#" else fullUrl

    def onClickHandler(e: ReactMouseEvent): Callback =
      if (isScalaCliTarget)
        e.preventDefaultCB >> Callback {
          // For scala-cli flow: insert the real logic here (e.g., open a modal,
          // trigger a small client-side flow, or call an API). Keep it lazy:
          dom.console.log(s"Scala CLI download requested for ${props.snippetId}")
        }
      else Callback.empty

    li(
      a(
        href := hrefAttr,
        download := downloadFilename,
        title := I18n.t("editor.download"),
        role := "button",
        cls := "btn",
        onClick ==> ((e: ReactMouseEvent) => onClickHandler(e))
      )(I18n.t("editor.download"))
    )
  }

  private val component =
    ScalaComponent
      .builder[DownloadButton]("DownloadButton")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
