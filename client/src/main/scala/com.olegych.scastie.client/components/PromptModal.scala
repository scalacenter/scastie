package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

object PrompModal {
  case class Props(
      modalText: String,
      isClosed: Boolean,
      close: Callback,
      actionText: String,
      actionLabel: String,
      action: Callback
  )

  def apply(props: Props) = component(props)

  private val component =
    ScalaComponent
      .builder[Props]("PrompModal")
      .render_P { props =>
        import props._

        Modal(modalText,
              isClosed,
              close,
              TagMod(clazz := "modal-reset"),
              TagMod(
                p(
                  clazz := "modal-intro",
                  actionText
                ),
                ul(
                  li(onClick ==> (e => e.stopPropagationCB >> action >> close),
                     clazz := "btn")(
                    actionLabel
                  ),
                  li(onClick ==> (e => e.stopPropagationCB >> close),
                     clazz := "btn")(
                    "Cancel"
                  )
                )
              ))
      }
      .build
}
