package com.olegych.scastie.client.components

import com.olegych.scastie.client.View
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

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
