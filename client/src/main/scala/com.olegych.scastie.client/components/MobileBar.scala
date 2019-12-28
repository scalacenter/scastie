package com.olegych.scastie.client.components

import com.olegych.scastie.client.View

import japgolly.scalajs.react._, vdom.all._, extra._

final case class MobileBar(isRunning: Boolean,
                           isStatusOk: Boolean,
                           run: Reusable[Callback],
                           setView: View ~=> Callback,
                           forceDesktop: Reusable[Callback]) {
  @inline def render: VdomElement = MobileBar.component(this)
}

object MobileBar {
  implicit val reusability: Reusability[MobileBar] =
    Reusability.derive[MobileBar]

  private def render(props: MobileBar): VdomElement = {
    nav(cls := "editor-mobile")(
      ul(cls := "editor-buttons")(
        RunButton(
          isRunning = props.isRunning,
          isStatusOk = props.isStatusOk,
          save = props.run,
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
      .configure(Reusability.shouldComponentUpdate)
      .build
}
