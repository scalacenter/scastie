package com.olegych.scastie
package client
package components

import api._

import japgolly.scalajs.react._, vdom.all._, extra.router.RouterCtl, extra._

import org.scalajs.dom

import org.scalajs.dom.html
import org.scalajs.dom.{window, document}

final case class ShareModal(router: RouterCtl[Page],
                            snippetId: SnippetId,
                            isClosed: Boolean,
                            close: Reusable[Callback]) {
  @inline def render: VdomElement =
    new ShareModal.ShareModalComponent().build(this)
}

object ShareModal {

  implicit val reusability: Reusability[ShareModal] =
    Reusability.caseClass[ShareModal]

  private class ShareModalComponent() {
    private var divRef: html.Div = _

    private def render(props: ShareModal): VdomElement = {
      val snippetUrl =
        props.router
          .urlFor(
            Page.fromSnippetId(props.snippetId)
          )
          .value

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
        props.isClosed,
        props.close,
        TagMod(cls := "modal-share"),
        TagMod(
          p(cls := "modal-intro")(
            "Copy and share your code snippet's URL:"
          ),
          div(cls := "snippet-link")(
            div.ref(divRef = _)(cls := "link-to-copy")(
              snippetUrl
            ),
            li(onClick --> copyLink,
               title := "Copy to Clipboard",
               cls := "snippet-clip clipboard-copy")(
              i(cls := "fa fa-clipboard")
            )
          )
        )
      ).render
    }

    private val component =
      ScalaComponent
        .builder[ShareModal]("ShareModal")
        .render_P(render)
        .configure(Reusability.shouldComponentUpdate)
        .build

    def build(props: ShareModal): VdomElement = component(props)
  }
}
