package org.scastie.client
package components

import japgolly.scalajs.react._
import org.scalajs.dom

import vdom.all._

import com.olegych.scastie.client.i18n.I18n
import japgolly.scalajs.react.hooks.HookCtx.I1


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

  private def renderWithLink(template: String, linkAction: Callback): VdomElement = {
    val linkRegex = """\{([^}]+)\}""".r
    
    linkRegex.findFirstMatchIn(template) match {
      case Some(m) =>
        val before = template.substring(0, m.start)
        val linkText = m.group(1)
        val after = template.substring(m.end)
        
        p(
          before,
          a(href := "#", onClick ==> (e => e.preventDefaultCB >> e.stopPropagationCB >> linkAction))(linkText),
          after
        )
      case None =>
        p(template)
    }
  }

  def render(props: LoginModal): VdomElement = {
    val theme = if (props.isDarkTheme) "dark" else "light"

    Modal(
      title = I18n.t("sidebar.login_title"),
      isDarkTheme = props.isDarkTheme,
      isClosed = props.isClosed,
      close = props.close,
      modalCss = TagMod(cls := s"$theme modal-login"),
      modalId = "modal-login",
      content = TagMod(
        button(onClick --> (login >> props.close), cls := "github-login")(
          i(cls := "fa fa-github"),
          I18n.t("sidebar.login_github"),
        ),
        renderWithLink(
          I18n.t("sidebar.login_agreement"),
          props.openPrivacyPolicyModal
        )
      )
    ).render
  }

  private val component =
    ScalaFnComponent
      .withHooks[LoginModal]
      .renderWithReuse(render)


}
