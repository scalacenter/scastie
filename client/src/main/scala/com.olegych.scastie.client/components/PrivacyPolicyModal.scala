package com.olegych.scastie.client.components

import japgolly.scalajs.react._
import scalajs.js
import vdom.all._
import com.olegych.scastie.client.components.editor.EditorKeymaps
import scala.scalajs.js.annotation.JSImport

final case class PrivacyPolicyModal(isDarkTheme: Boolean, isClosed: Boolean, close: Reusable[Callback]) {
  @inline def render: VdomElement = PrivacyPolicyModal.component(this)
}

object PrivacyPolicyModal {
  implicit val reusability: Reusability[PrivacyPolicyModal] =
    Reusability.derive[PrivacyPolicyModal]

  @js.native
  @JSImport("@scastieRoot/privacy-policy.md", "html")
  val privacyPolicyHTMLContent: String = js.native

  private def render(props: PrivacyPolicyModal): VdomElement = {
    val theme = if (props.isDarkTheme) "dark" else "light"

    Modal(
      title = "Scastie privacy policy\n",
      isDarkTheme = props.isDarkTheme,
      isClosed = props.isClosed,
      close = props.close,
      modalCss = TagMod(cls := theme),
      modalId = "privacy-policy",
      content = div(cls := "markdown-body", dangerouslySetInnerHtml := privacyPolicyHTMLContent)
    ).render
  }

  private val component =
    ScalaFnComponent
      .withHooks[PrivacyPolicyModal]
      .renderWithReuse(render)
}
