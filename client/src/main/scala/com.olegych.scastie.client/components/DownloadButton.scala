package com.olegych.scastie.client.components

import com.olegych.scastie.api.SnippetId
import com.olegych.scastie.client.View._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

final case class DownloadButton(saveAndDownload: Callback,
                                snippetId: Option[SnippetId]) {
  @inline def render: VdomElement = DownloadButton.component(this)
}

object DownloadButton {
  def render(props: DownloadButton): VdomElement = {
    props.snippetId match {
      case Some(sid) =>
        a(
          href := s"/download/${sid.base64UUID}",
          download := s"${sid.base64UUID}",
          li(title := s"Download ($ctrl + D)", role := "button", cls := "btn")(
            i(cls := "fa fa-download"),
            span("Download")
          )
        )

      case None =>
        li(title := s"Download ($ctrl + D)",
           role := "button",
           cls := "btn",
           onClick --> props.saveAndDownload)(
          i(cls := "fa fa-download"),
          span("Download")
        )
    }
  }

  private val component =
    ScalaComponent
      .builder[DownloadButton]("DownloadButton")
      .render_P(render)
      .build
}
