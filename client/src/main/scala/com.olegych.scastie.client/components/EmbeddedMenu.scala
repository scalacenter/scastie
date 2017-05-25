package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._, vdom.all._

final case class EmbeddedMenu(isRunning: Boolean,
                              isStatusOk: Boolean,
                              run: Callback,
                              setView: View => Callback,
                              clear: Callback) {
  @inline def render: VdomElement = EmbeddedMenu.component(this)
}

object EmbeddedMenu {
  private def render(props: EmbeddedMenu): VdomElement = {
    div(cls := "embedded-menu")(
      RunButton(
        isRunning = props.isRunning,
        isStatusOk = props.isStatusOk,
        run = props.run,
        setView = props.setView
      ).render,
      ClearButton(
        clear = props.clear
      ).render
    )
  }

  private val component =
    ScalaComponent
      .builder[EmbeddedMenu]("EmbeddedMenu")
      .render_P(render)
      .build
}
