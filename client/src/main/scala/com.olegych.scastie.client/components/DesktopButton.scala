package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._, vdom.all._

final case class DesktopButton(forceDesktop: Callback) {
  @inline def render: VdomElement = DesktopButton.component(this)
}

object DesktopButton {

  private def render(props: DesktopButton): VdomElement = {
    li(title := "Go to desktop", cls := "btn", onClick --> props.forceDesktop)(
      i(cls := "fa fa-desktop"),
      span("Desktop")
    )
  }

  private val component =
    ScalaComponent
      .builder[DesktopButton]("DesktopButton")
      .render_P(render)
      .build
}
