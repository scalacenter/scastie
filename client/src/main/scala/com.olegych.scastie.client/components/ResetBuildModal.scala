package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object ResetBuildModal {

  def apply(state: AppState, backend: AppBackend) =
    component((state, backend))

  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend)]("ResetBuildModal")
      .render_P {
        case (state, backend) =>
          val displayReset =
            if (state.modalState.isResetModalClosed) display.none
            else display.block

          div(clazz := "modal", displayReset)(
            div(clazz := "modal-fade-screen")(
              div(clazz := "modal-window  modal-reset")(
                div(clazz := "modal-header")(
                  div(clazz := "modal-close",
                      onClick ==> backend.toggleReset)
                )(
                  h1("Reset Configuration")
                ),
                div(clazz := "modal-inner")(
                  p(
                    clazz := "modal-intro",
                    "Are you sure you want to reset the current configuration?"
                  ),
                  ul(
                    li(onClick ==> backend.resetBuildAndClose,
                       clazz := "btn")(
                      "Accept"
                    ),
                    li(onClick ==> backend.toggleReset, clazz := "btn")(
                      "Cancel"
                    )
                  )
                )
              )
            )
          )
      }
      .build
}
