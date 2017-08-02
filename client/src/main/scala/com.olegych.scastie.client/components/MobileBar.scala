package com.olegych.scastie.client.components

import com.olegych.scastie.client.View
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

final case class MobileBar(isRunning: Boolean,
                           isStatusOk: Boolean,
                           run: Callback,
                           setView: View => Callback,
                           forceDesktop: Callback) {
  @inline def render: VdomElement = MobileBar.component(this)
}

object MobileBar {
  private def render(props: MobileBar): VdomElement = {
    nav(cls := "editor-mobile")(
      ul(cls := "editor-buttons")(
        RunButton(
          isRunning = props.isRunning,
          isStatusOk = props.isStatusOk,
          run = props.run,
          setView = props.setView
        ).render,
        DesktopButton(
          forceDesktop = props.forceDesktop
        ).render
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[MobileBar]("MobileBar")
      .render_P(render)
      .build
}
