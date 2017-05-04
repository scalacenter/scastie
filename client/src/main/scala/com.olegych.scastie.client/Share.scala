package com.olegych.scastie.client

import japgolly.scalajs.react._, extra.router.RouterCtl, vdom.all._
// import org.scalajs.dom

object Share {

  def apply(router: Option[RouterCtl[Page]],
            state: AppState,
            backend: AppBackend) =
    component((router, state, backend))

  private val component =
    ScalaComponent.builder[(Option[RouterCtl[Page]], AppState, AppBackend)]("Share")
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

          div(`class` := "modal", displayShare)(
            div(`class` := "modal-fade-screen")(
              div(`class` := "modal-window  modal-share")(
                div(`class` := "modal-header")(
                  div(`class` := "modal-close",
                      onClick ==> backend.toggleShare(state.snippetId))
                )(
                  h1("Share your Code Snippet")
                ),
                div(`class` := "modal-inner")(
                  p(`class` := "modal-intro",
                    "Copy and share your code snippet's URL:"),
                  div(`class` := "snippet-link")(
                    div(
                      `class` := "link-to-copy",
                      getSnippetUrl
                    ),
                    li(`class` := "clipboard-copy",
                       title := "Copy to Clipboard",
                       `class` := "btn snippet-clip")(
                      i(`class` := "fa fa-clipboard")
                    )
                  )
                )
              )
            )
          )
      }
      // .componentDidMount { c =>
      //   def copyLink(e0: dom.Event) = {

      //     val contentHolder = dom.document.getElementById("link-to-copy")

      //     val range = dom.document.createRange()

      //     val selection = dom.window.getSelection()

      //     range.selectNodeContents(contentHolder)

      //     selection.addRange(range)

      //     dom.document.execCommand("copy")

      //   }

      //   Callback(
      //     dom.document
      //       .getElementById("clipboard-copy")
      //       .addEventListener("click", copyLink _)
      //   )

      // }
      .build
}
