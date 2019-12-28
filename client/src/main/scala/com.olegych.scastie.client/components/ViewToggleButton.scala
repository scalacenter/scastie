package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._, vdom.all._, extra._

final case class ViewToggleButton(currentView: StateSnapshot[View],
                                  forView: View,
                                  buttonTitle: String,
                                  faIcon: String,
                                  onClick: Reusable[Callback]) {
  @inline def render: VdomElement = ViewToggleButton.component(this)
}

object ViewToggleButton {

  implicit val reusability: Reusability[ViewToggleButton] =
    Reusability.derive[ViewToggleButton]

  private def render(props: ViewToggleButton): VdomElement = {
    li(
      onClick --> (props.currentView.setState(props.forView) >> props.onClick),
      role := "button",
      title := props.buttonTitle,
      (cls := "selected").when(props.currentView.value == props.forView),
      cls := "btn"
    )(
      i(cls := props.faIcon, cls := "fa"),
      span(props.buttonTitle)
    )
  }

  private val component =
    ScalaComponent
      .builder[ViewToggleButton]("ViewToggleButton")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
