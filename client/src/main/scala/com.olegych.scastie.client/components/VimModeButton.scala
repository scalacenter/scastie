package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import vdom.all._

final case class VimModeButton(
    isVimMode: Boolean,
    toggleVimMode: Reusable[Callback],
    view: View
) {
  @inline def render: VdomElement = VimModeButton.component(this)
}

object VimModeButton {

  implicit val reusability: Reusability[VimModeButton] =
    Reusability.derive[VimModeButton]

  private def render(props: VimModeButton): VdomElement = {
    val isVimModeSelected =
      if (props.isVimMode)
        if (props.view != View.Editor)
          TagMod(cls := "enabled alpha")
        else
          TagMod(cls := "enabled")
      else
        EmptyVdom

    val isVimModeToggleLabel =
      if (props.isVimMode) "OFF"
      else "ON"

    li(
      title := s"Turn Vim mode $isVimModeToggleLabel (use Vim keybindings in the editor)",
      isVimModeSelected,
      role := "button",
      cls := "btn editor",
      onClick --> props.toggleVimMode
    )(
      i(cls := "fa fa-keyboard-o"),
      span("Vim"),
      i(cls := "vimModeIndicator", cls := "fa fa-circle", isVimModeSelected)
    )
  }

  private val component =
    ScalaComponent
      .builder[VimModeButton]("VimModeButton")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}