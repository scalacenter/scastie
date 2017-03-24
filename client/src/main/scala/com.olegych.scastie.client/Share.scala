package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

object Share {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ReactComponentB[(AppState, AppBackend)]("Share").render_P {
      case (state, backend) =>

      val displayShare =
        if (state.isShareModalClosed) display.none
        else display.block

        div(`class` := "modal", displayShare)(
          input(`type` := "checkbox", `id` := "modal-share-snippet", `class` := "modal-state"),
          div(`class` := "modal-fade-screen")(
            div(`class` := "modal-window  modal-share")(
              div(`class` := "modal-header")(
                div(`for` := "modal-welcome", `class` := "modal-close", onClick ==> backend.toggleShare))(
                h1("Share your Code Snippet")
              ),
              div(`class` := "modal-inner")(
                p(`class` := "modal-intro","Share your code snippet:")
              )
            )
          )
        )

    }.build
}
