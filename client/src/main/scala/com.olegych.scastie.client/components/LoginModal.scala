package com.olegych.scastie.client
package components

import japgolly.scalajs.react._
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.html
import org.scalajs.dom.window
import vdom.all._


final case class LoginModal(
  isDarkTheme: Boolean,
  isClosed: Boolean,
  close: Reusable[Callback],
  openPrivacyPolicyModal: Reusable[Callback]
) {
  @inline def render: VdomElement = LoginModal.component(this)
}

object LoginModal {

  implicit val reusability: Reusability[LoginModal] = Reusability.derive[LoginModal]


  def login: Callback =
    Callback(dom.window.location.pathname = "/login")

  def render(props: LoginModal): VdomElement = {
    val theme = if (props.isDarkTheme) "dark" else "light"

    Modal(
      title = "Login to Scastie",
      isDarkTheme = props.isDarkTheme,
      isClosed = props.isClosed,
      close = props.close,
      modalCss = TagMod(cls := s"$theme modal-login"),
      modalId = "modal-login",
      content = TagMod(
        button(onClick --> (login >> props.close), cls := "github-login")(
          i(cls := "fa fa-github"),
          "Continue with GitHub",
        ),
        p(
          "By signing in, you agree to our ",
          a(href := "javascript:;", onClick ==> (e => e.stopPropagationCB >> props.openPrivacyPolicyModal))("privacy policy"),
          "."
        )
      )
    ).render
  }

  private val component =
    ScalaFnComponent
      .withHooks[LoginModal]
      .renderWithReuse(render)


}
