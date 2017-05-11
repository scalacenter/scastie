package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object NewButton {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend)]("NewButton")
      .render_P {
        case (state, backend) =>
          li(title := "New code snippet",
             role := "button",
             onClick --> backend.openNewSnippetModal,
             clazz := "btn")(
            i(clazz := "fa fa-file-o"),
            span("New"),
            PrompModal(
              PrompModal.Props(
                modalText = "New Snippet",
                isClosed = state.modalState.isNewSnippetModalClosed,
                close = backend.closeNewSnippetModal,
                actionText = "Are you sure you want to create a new snippet ?",
                actionLabel = "New",
                action = backend.newSnippet
              )
            )
          )
      }
      .build
}
