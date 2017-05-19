package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object Modal {
  def apply(title: String,
            isClosed: Boolean,
            close: Callback,
            modalClazz: TagMod,
            content: TagMod) =
    component((title, isClosed, close, modalClazz, content))

  private val component =
    ScalaComponent
      .builder[(String, Boolean, Callback, TagMod, TagMod)]("Modal")
      .render_P {
        case (titleText, isClosed, close, modalClazz, content) =>
          val modalStyle =
            if (isClosed) TagMod(display.none)
            else TagMod(display.block)

          div(clazz := "modal", modalStyle)(
            div(clazz := "modal-fade-screen", onClick ==> (e => e.stopPropagationCB >> close))(
              div(clazz := "modal-window", modalClazz, onClick ==> (e => e.stopPropagationCB))(
                div(clazz := "modal-header")(
                  div(clazz := "modal-close",
                      onClick ==> (e => e.stopPropagationCB >> close),
                      role := "button",
                      title := "close help modal")
                )(
                  h1(titleText)
                ),
                div(clazz := "modal-inner")(
                  content
                )
              )
            )
          )
      }
      .build
}
