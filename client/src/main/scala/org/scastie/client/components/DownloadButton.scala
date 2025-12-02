package org.scastie.client.components

import org.scastie.api._
import japgolly.scalajs.react._
import vdom.all._
import org.scastie.client.i18n.I18n

final case class DownloadButton(
  snippetId: SnippetId,
  scalaTarget: ScalaTarget,
  language: String,
  onClick: Option[Callback] = None
) {
  @inline def render: VdomElement = DownloadButton.component(this)
}

object DownloadButton {
  implicit val reusability: Reusability[DownloadButton] = Reusability.never

  private def filenameFromSnippetId(snippetId: SnippetId): String = {
    val raw = snippetId.toString
    raw.replaceAll("[^A-Za-z0-9\\.\\-]+", "-")
       .replaceAll("-{2,}", "-")
       .replaceAll("(^-+)|(-+$)", "")
  }

  private def downloadUrl(snippetId: SnippetId, language: String): String =
    s"/api/download/${snippetId.toString}/${language}"

  def render(props: DownloadButton): VdomElement = {
    val isScalaCliTarget =
      props.scalaTarget.targetType == ScalaTargetType.ScalaCli

    val filenameBase = filenameFromSnippetId(props.snippetId)
    val downloadFilename = s"$filenameBase.zip"
    val fullUrl = downloadUrl(props.snippetId, props.language)
    
    val hrefAttr = if (isScalaCliTarget) "#" else fullUrl

    def handleClick(e: ReactMouseEvent): Callback = {
      props.onClick match {
        case Some(cb) if isScalaCliTarget => 
          e.preventDefaultCB >> cb
        case _ => 
          Callback.empty
      }
    }

    li(
      a(
        href := hrefAttr,
        download := downloadFilename,
        title := I18n.t("editor.download"),
        role := "button",
        cls := "btn",
        onClick ==> handleClick
      )(
        **i(cls := "fa fa-download"),**
        I18n.t("editor.download"))
    )
  }

  private val component =
    ScalaComponent
      .builder[DownloadButton]("DownloadButton")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
