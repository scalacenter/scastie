package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._, vdom.all._, extra.{Reusability, StateSnapshot}

final case class ViewToggleButton(currentView: StateSnapshot[View],
                                  forView: View,
                                  buttonTitle: String,
                                  faIcon: String) {
  @inline def render: VdomElement = ViewToggleButton.component(this)
}

object ViewToggleButton {
  implicit val reusability: Reusability[ViewToggleButton] =
    Reusability.caseClass

  private def render(props: ViewToggleButton): VdomElement = {
    li(
      onClick --> props.currentView.setState(props.forView),
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
