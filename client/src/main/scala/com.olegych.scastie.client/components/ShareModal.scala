package com.olegych.scastie
package client
package components

import api._

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}
import japgolly.scalajs.react.extra.router.RouterCtl

import org.scalajs.dom

import org.scalajs.dom.html
import org.scalajs.dom.{window, document}

object ShareModal {
  def apply(router: RouterCtl[Page],
            snippetId: SnippetId,
            isClosed: Boolean,
            close: Callback) =
    new ShareModal(router, snippetId, isClosed, close).build

  private class ShareModal(router: RouterCtl[Page],
                           snippetId: SnippetId,
                           isClosed: Boolean,
                           close: Callback) {

    private var divRef: html.Div = _

    private val component =
      ScalaComponent
        .builder[(RouterCtl[Page], SnippetId, Boolean, Callback)]("ShareModal")
        .render_P {
          case (router, snippetId, isClosed, close) =>
            val snippetUrl = router.urlFor(Page.fromSnippetId(snippetId)).value

            def copyLink: Callback = Callback {
              val range = dom.document.createRange()
              val selection = dom.window.getSelection()
              range.selectNodeContents(divRef)
              selection.addRange(range)
              if (!document.execCommand("copy")) {
                window.alert("cannot copy link")
              }
            }

            Modal(
              "Share your Code Snippet",
              isClosed,
              close,
              TagMod(clazz := "modal-share"),
              TagMod(
                p(clazz := "modal-intro")(
                  "Copy and share your code snippet's URL:"
                ),
                div(clazz := "snippet-link")(
                  div.ref(divRef = _)(clazz := "link-to-copy")(
                    snippetUrl
                  ),
                  li(onClick --> copyLink,
                     title := "Copy to Clipboard",
                     clazz := "snippet-clip clipboard-copy")(
                    i(clazz := "fa fa-clipboard")
                  )
                )
              )
            )
        }
        .build

    def build = component((router, snippetId, isClosed, close))
  }
}
