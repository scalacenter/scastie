package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._, vdom.all._, extra._

final case class DesktopButton(forceDesktop: Reusable[Callback]) {
  @inline def render: VdomElement = DesktopButton.component(this)
}

object DesktopButton {
  implicit val reusability: Reusability[DesktopButton] =
    Reusability.caseClass[DesktopButton]

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
      .configure(Reusability.shouldComponentUpdateWithOverlay)
      .build
}
