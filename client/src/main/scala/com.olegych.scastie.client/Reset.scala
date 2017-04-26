package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object Reset {

  def apply(state: AppState, backend: AppBackend) =
    component((state, backend))

  private val component =
    ScalaComponent.builder[(AppState, AppBackend)]("Reset").render_P {
      case (state, backend) =>
        val displayReset =
          if (state.modalState.isResetModalClosed) display.none
          else display.block

        div(`class` := "modal", displayReset)(
          div(`class` := "modal-fade-screen")(
            div(`class` := "modal-window  modal-reset")(
              div(`class` := "modal-header")(
                div(`class` := "modal-close", onClick ==> backend.toggleReset)
              )(
                h1("Reset Configuration")
              ),
              div(`class` := "modal-inner")(
                p(`class` := "modal-intro",
                  "Are you sure you want to reset the current configuration?"),
                ul(
                  li(onClick ==> backend.resetBuildAndClose, `class` := "btn")(
                    "Accept"
                  ),
                  li(onClick ==> backend.toggleReset, `class` := "btn")(
                    "Cancel"
                  )
                )
              )
            )
          )
        )
    }.build
}
