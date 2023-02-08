package com.olegych.scastie.client
package components


import com.olegych.scastie.client.components.editor.EditorKeymaps
import japgolly.scalajs.react._
import org.scalajs.dom

import vdom.all._
import extra._


// scheduled for removal 2023-04-30
@deprecated("Scheduled for removal", "2023-04-30")
final case class PrivacyPolicyPrompt(
                           isDarkTheme: Boolean,
                           isClosed: Boolean,
                           acceptPrivacyPolicy: Reusable[Callback],
                           refusePrivacyPolicy: Reusable[Callback],
                           openPrivacyPolicyModal: Reusable[Callback]
                         ) {
  @inline def render: VdomElement = PrivacyPolicyPrompt.component(this)
}


@deprecated("Scheduled for removal", "2023-04-30")
object PrivacyPolicyPrompt {
  implicit val reusability: Reusability[PrivacyPolicyPrompt] =
    Reusability.derive[PrivacyPolicyPrompt]

  def reloadWindow = Reusable.always(Callback { dom.window.location.reload() })

  def render(props: PrivacyPolicyPrompt): VdomElement = {
    val theme = if (props.isDarkTheme) "dark" else "light"

    Modal(
      title = "Privacy Policy Introduction",
      isDarkTheme = props.isDarkTheme,
      isClosed = props.isClosed,
      close = Reusable.always(Callback {}),
      modalCss = TagMod(cls := s"$theme modal-reset privacy-policy-prompt"),
      modalId = "privacy-policy-prompt",
      content = TagMod(
        div(cls := "modal-intro")(
          p("""With the introduction of privacy policy to Scastie, you have to decide
              | whether you want to keep your existing code snippets, or remove them all from our database.
              | By keeping the snippets, you acknowledge that you have read and agreed
              | to the privacy policy terms available """.stripMargin.stripLineEnd,
            a(href := "javascript:;", onClick ==> (e => e.stopPropagationCB >> props.openPrivacyPolicyModal))(
              "here"
            ),
            "."
          ),
          p(
            """The deletion of your code snippets is a permanent option which completely
              | removes any data associated with your GitHub login from our database.
              | Data removal does not remove your user from GitHub OAuth2 apps.
              | It can be removed manually """.stripMargin.stripLineEnd,
            a(href := "https://github.com/settings/connections/applications/d7cbfb03ca41da894a75", target := "_blank")(
              "here"
            ),
            """. Before revoking OAuth access, make sure to delete your snippets,
              |as we will not be able to identify you afterwards.""".stripMargin.stripLineEnd
          ),
          p(
            """If you do not explicitly ask us to keep your snippets before April 30th 2023, we will delete them all."""
          ),
        ),
        ul(
          li(onClick ==> (e => e.stopPropagationCB >> props.acceptPrivacyPolicy), cls := "btn")(
            "Keep my existing snippets"
          ),
        li(onClick ==> (e =>
            e.stopPropagationCB >> props.refusePrivacyPolicy
          ), cls := "btn")(
            "Delete my existing snippets"
          )
        )
      )
    ).render
  }

  private val component =
    ScalaFnComponent
      .withHooks[PrivacyPolicyPrompt]
      .renderWithReuse(render)
}
