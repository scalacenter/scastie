package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.all._

object Share {

  def apply(router: Option[RouterCtl[Page]], state: AppState, backend: AppBackend) =
    component((router, state, backend))

  private val component =
    ReactComponentB[(Option[RouterCtl[Page]], AppState, AppBackend)]("Share").render_P {
      case (maybeRouter, state, backend) =>

      val displayShare =
        if (state.isShareModalClosed) display.none
        else display.block

      val displayCopied =
        if (state.codeSnippetCopied) display.none
        else display.none

      def getSnippetUrl =
        (maybeRouter, state.snippetId) match {
          case (Some(router), Some(snippetId)) => router.urlFor(Page.fromSnippetId(snippetId)).value
          case _ => ""
        }

      div(`class` := "modal", displayShare)(
        div(`class` := "modal-fade-screen")(
          div(`class` := "modal-window  modal-share")(
            div(`class` := "modal-header")(
              div(`class` := "modal-close", onClick ==> backend.toggleShare(state.snippetId)))(
              h1("Share your Code Snippet")
            ),
            div(`class` := "modal-inner")(
              p(`class` := "modal-intro", "Copy and share your code snippet's URL:"),
              div(`class` := "snippet-link")(
                input.text(
                  placeholder := "Snippet URl not found",
                  value := getSnippetUrl,
                  readOnly := true
                ),
                li(
                  title := "Copy to Clipboard",
                  `class` := "btn snippet-clip", onClick ==> backend.toggleSnippetCopied, display.none)(
                  i(`class` := "fa fa-clipboard")
                ),
                p(`class` := "modal-copied", "Copied!", displayCopied)
              )
            )
          )
        )
      )

    }.build
}
