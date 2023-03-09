package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.html
import org.scalajs.dom.window
import vdom.all._

final case class CopyModal(
  isDarkTheme: Boolean,
  title: String,
  subtitle: String,
  content: String,
  modalId: String,
  isClosed: Boolean,
  close: Reusable[Callback]
) {
  @inline def render: VdomElement = new CopyModal.ShareModalComponent().build(this)
}

object CopyModal {

  implicit val reusability: Reusability[CopyModal] = Reusability.derive[CopyModal]

  private class ShareModalComponent() {
    private val divRef = Ref[html.Div]

    private def render(props: CopyModal): VdomElement = {
      def copyLink: Callback = divRef.get.map { divRef =>
        val range     = dom.document.createRange()
        val selection = dom.window.getSelection()
        divRef.foreach(range.selectNodeContents)
        selection.addRange(range)
        if (!document.execCommand("copy")) {
          window.alert("cannot copy link")
        }
      }

      Modal(
        title = props.title,
        isDarkTheme = props.isDarkTheme,
        isClosed = props.isClosed,
        close = props.close,
        modalCss = TagMod(cls := "modal-share"),
        modalId = props.modalId,
        content = TagMod(
          p(cls := "modal-intro")(
            props.subtitle
          ),
          div(cls := "snippet-link")(
            div.withRef(divRef)(cls := "link-to-copy", onClick --> copyLink)(
              props.content
            ),
            div(onClick --> copyLink, title := "Copy to Clipboard", cls := "snippet-clip clipboard-copy")(
              i(cls                         := "fa fa-clipboard")
            )
          )
        )
      ).render
    }

    private val component = ScalaComponent
      .builder[CopyModal]("CopyModal")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build

    def build(props: CopyModal): VdomElement = component(props)
  }

}
