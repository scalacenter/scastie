package org.scastie.client
package components

import org.scastie.api._
import japgolly.scalajs.react._
import vdom.all._
import org.scalajs.dom
import scala.scalajs.js
import scala.collection.concurrent.TrieMap
import org.scastie.client.i18n.I18n

final case class DownloadButton(snippetId: SnippetId, scalaTarget: ScalaTarget, language: String) {
  @inline def render: VdomElement = DownloadButton.component(this)
}

object DownloadButton {
  implicit val reusability: Reusability[DownloadButton] =
    Reusability.derive[DownloadButton]

  private def filesystemFriendlyName(snippetId: SnippetId): String = {
    val raw = snippetId.toString
    raw.replaceAll("[^A-Za-z0-9\\.\\-]+", "-")
       .replaceAll("-{2,}", "-")
       .replaceAll("(^-+)|(-+$)", "")
  }

  private def downloadUrl(snippetId: SnippetId, language: String): String =
    s"/api/download/${snippetId.toString}/${language}"

  def render(props: DownloadButton): VdomElement = {
    val isScalaCliTarget = props.scalaTarget.targetType == ScalaTargetType.ScalaCli
    val filenameBase = filesystemFriendlyName(props.snippetId)
    val downloadFilename = s"$filenameBase.zip"
    val fullUrl = downloadUrl(props.snippetId, props.language)
    val hrefAttr = if (isScalaCliTarget) "#" else fullUrl

    def onClickHandler(e: ReactMouseEvent): Callback =
      if (isScalaCliTarget) {
        e.preventDefaultCB >> Callback {
          dom.console.log(s"Scala CLI download requested for ${props.snippetId}")
        }
      } else Callback.empty

    li(
      a(
        href := hrefAttr,
        download := downloadFilename,
        title := I18n.t("editor.download"),
        role := "button",
        cls := "btn",
        onClick ==> (e => onClickHandler(e))
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

