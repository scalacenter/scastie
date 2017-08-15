package com.olegych.scastie.client
package components

import com.olegych.scastie.api.{SnippetId, SnippetUserPart}

import japgolly.scalajs.react._, vdom.all._

final case class DownloadButton(snippetId: SnippetId) {
  @inline def render: VdomElement = DownloadButton.component(this)
}

object DownloadButton {
  def render(props: DownloadButton): VdomElement = {
    val url = props.snippetId.url
    val fullUrl = s"/api/download/$url"

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
