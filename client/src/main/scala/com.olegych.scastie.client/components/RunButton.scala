package com.olegych.scastie.client.components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

final case class RunButton(isRunning: Boolean,
                           isStatusOk: Boolean,
                           run: Callback,
                           setView: View => Callback) {
  @inline def render: VdomElement = RunButton.component(this)
}

object RunButton {
  def render(props: RunButton): VdomElement = {
    if (!props.isRunning) {
      val runTitle =
        if (props.isStatusOk)
          s"Run Code (${View.ctrl} + Enter)"
        else
          "Something is wrong check the status"

      val run =
        if (props.isStatusOk) props.run
        else Callback.empty

      li(onClick --> run,
         role := "button",
         title := runTitle,
         (cls := "disabled").when(!props.isStatusOk),
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
