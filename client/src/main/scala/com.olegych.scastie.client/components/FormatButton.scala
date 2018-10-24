package com.olegych.scastie.client.components

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra._
import japgolly.scalajs.react.vdom.all._

final case class FormatButton(inputsHasChanged: Boolean, isStatusOk: Boolean, formatCode: Reusable[Callback]) {
  @inline def render: VdomElement = FormatButton.component(this)
}

object FormatButton {
  implicit val reusability: Reusability[FormatButton] =
    Reusability.caseClass[FormatButton]

  private def render(props: FormatButton): VdomElement = {
    val isDisabled = false

    val formatCode =
      if (!isDisabled) props.formatCode
      else reusableEmpty

    li(title := "Format Code (F6)", role := "button", (cls := "disabled").when(isDisabled), cls := "btn", onClick --> formatCode)(
      i(cls := "fa fa-align-left"),
      span("Format")
    )
  }

  private val component =
    ScalaComponent
      .builder[FormatButton]("FormatButton")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
