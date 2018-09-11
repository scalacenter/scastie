package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._, vdom.all._, extra._

import org.scalajs.dom

import org.scalajs.dom.html
import org.scalajs.dom.{window, document}

final case class CopyModal(title: String, subtitle: String, content: String, modalId: String, isClosed: Boolean, close: Reusable[Callback]) {
  @inline def render: VdomElement =
    new CopyModal.ShareModalComponent().build(this)
}

object CopyModal {

  implicit val reusability: Reusability[CopyModal] =
    Reusability.caseClass[CopyModal]

  private class ShareModalComponent() {
    private var divRef: html.Div = _

    private def render(props: CopyModal): VdomElement = {
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
        title = props.title,
        isClosed = props.isClosed,
        close = props.close,
        modalCss = TagMod(cls := "modal-share"),
        modalId = props.modalId,
        content = TagMod(
          p(cls := "modal-intro")(
            props.subtitle
          ),
          div(cls := "snippet-link")(
            div.ref(divRef = _)(cls := "link-to-copy")(
              props.content
            ),
            li(onClick --> copyLink, title := "Copy to Clipboard", cls := "snippet-clip clipboard-copy")(
              i(cls := "fa fa-clipboard")
            )
          )
        )
      ).render
    }

    private val component =
      ScalaComponent
        .builder[CopyModal]("CopyModal")
        .render_P(render)
        .configure(Reusability.shouldComponentUpdate)
        .build

    def build(props: CopyModal): VdomElement = component(props)
  }
}
