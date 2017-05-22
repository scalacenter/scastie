package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._, vdom.all._

final case class RunButton(isRunning: Boolean,
                           run: Callback,
                           setView: View => Callback) {
  @inline def render: VdomElement = RunButton.component(this)
}

object RunButton {
  def render(props: RunButton) = {
    if (!props.isRunning) {
      li(onClick --> props.run,
         role := "button",
         title := s"Run Code (${View.ctrl} + Enter)",
         cls := "btn run-button")(
        i(cls := "fa fa-play"),
        span("Run")
      )
    } else {
      li(onClick --> props.setView(View.Editor),
         title := "Running your Code...",
         cls := "btn run-button")(
        i(cls := "fa fa-spinner fa-spin"),
        span("Running")
      )
    }
  }

  private val component =
    ScalaComponent
      .builder[RunButton]("RunButton")
      .render_P(render)
      .build
}
