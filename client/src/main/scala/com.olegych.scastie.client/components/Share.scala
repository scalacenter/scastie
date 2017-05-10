package com.olegych.scastie
package client
package components


import japgolly.scalajs.react._, vdom.all._
// import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}
import japgolly.scalajs.react.extra.router.RouterCtl

import org.scalajs.dom

object Share {

  def apply(router: Option[RouterCtl[Page]],
            state: AppState,
            backend: AppBackend) =
    component((router, state, backend))

  private val component =
    ScalaComponent
      .builder[(Option[RouterCtl[Page]], AppState, AppBackend)]("Share")
      .render_P {
        case (maybeRouter, state, backend) =>
          val displayShare =
            if (state.modalState.isShareModalClosed) display.none
            else display.block

          def getSnippetUrl =
            (maybeRouter, state.snippetId) match {
              case (Some(router), Some(snippetId)) =>
                router.urlFor(Page.fromSnippetId(snippetId)).value
              case _ => "Snippet URl not found"
            }

          div("modal", displayShare)(
            div("modal-fade-screen")(
              div("modal-window  modal-share")(
                div("modal-header")(
                  div("modal-close",
                      onClick ==> backend.toggleShare(state.snippetId))
                )(
                  h1("Share your Code Snippet")
                ),
                div("modal-inner")(
                  p("modal-intro",
                    "Copy and share your code snippet's URL:"),
                  div("snippet-link")(
                    div(
                      id := "link-to-copy",
                      getSnippetUrl
                    ),
                    li(id := "clipboard-copy",
                       title := "Copy to Clipboard",
                       "btn snippet-clip")(
                      i("fa fa-clipboard")
                    )
                  )
                )
              )
            )
          )
      }
      .componentDidMount { c =>
        def copyLink(e0: dom.Event) = {
          val contentHolder = dom.document.getElementById("link-to-copy")
          val range = dom.document.createRange()
          val selection = dom.window.getSelection()
          range.selectNodeContents(contentHolder)
          selection.addRange(range)
          dom.document.execCommand("copy")
        }

        Callback(
          dom.document
            .getElementById("clipboard-copy")
            .addEventListener("click", copyLink _)
        )
      }
      .build
}
