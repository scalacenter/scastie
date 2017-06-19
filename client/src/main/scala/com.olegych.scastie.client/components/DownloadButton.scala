package com.olegych.scastie
package client
package components

import api.{SnippetId, SnippetUserPart}
import View.ctrl

import japgolly.scalajs.react._, vdom.all._

final case class DownloadButton(snippetId: SnippetId) {
  @inline def render: VdomElement = DownloadButton.component(this)
}

object DownloadButton {
  def render(props: DownloadButton): VdomElement = {
    val url =
      props.snippetId match {
        case SnippetId(base64UUID, None) =>
          s"$base64UUID"

        case SnippetId(base64UUID, Some(SnippetUserPart(login, None))) =>
          s"$login/$base64UUID/0"

        case SnippetId(base64UUID,
                       Some(SnippetUserPart(login, Some(update)))) =>
          s"$login/$base64UUID/$update"
      }

    val fullUrl = s"/download/$url"

    li(
      a(href := fullUrl,
        download := url.replaceAll("/", "-") + ".zip",
        title := s"Download ($ctrl + D)",
        role := "button",
        cls := "btn")(
        i(cls := "fa fa-download"),
        span("Download")
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[DownloadButton]("DownloadButton")
      .render_P(render)
      .build
}
