package org.scastie
package client
package components

import org.scalajs.dom

import org.scastie.api.User
import org.scastie.client.i18n.I18n

import japgolly.scalajs.react._

import extra._
import vdom.all._

final case class TopBar(
    view: StateSnapshot[View],
    user: Option[User],
    openLoginModal: Reusable[Callback],
    setLanguage: String ~=> Callback,
    language: String,
    isDarkTheme: Boolean
) {
  @inline def render: VdomElement = TopBar.component(this)
}

object TopBar {

  implicit val reusability: Reusability[TopBar] = Reusability.derive[TopBar]

  private def render(props: TopBar): VdomElement = {
    def openInNewTab(link: String): Callback = Callback {
      dom.window.open(link, "_blank").focus()
    }

    def feedback: Callback = openInNewTab("https://gitter.im/scalacenter/scastie")

    def issue: Callback = openInNewTab("https://github.com/scalacenter/scastie/issues/new/choose")

    val logoutUrl = "/logout"

    def logout: Callback = props.view.setState(View.Editor) >>
      Callback(dom.window.location.pathname = logoutUrl)

    val profileButton = props.user match {
      case Some(user) => li(
          cls := "btn dropdown",
          img(src := user.avatar_url + "&s=30", alt := "Your Github Avatar", cls := "avatar"),
          span(user.login),
          i(cls := "fa fa-caret-down"),
          ul(
            cls := "subactions",
            li(
              onClick --> props.view.setState(View.CodeSnippets),
              role := "link",
              title := I18n.t("topbar.snippets_tooltip"),
              cls := "btn",
              (cls := "selected").when(View.CodeSnippets == props.view.value)
            )(
              i(cls := "fa fa-code"),
              I18n.t("topbar.snippets")
            ),
            li(role := "link", onClick --> logout, cls := "btn", i(cls := "fa fa-sign-out"), I18n.t("topbar.logout"))
          )
        )

      case None => li(
          role := "link",
          onClick --> props.openLoginModal,
          cls := "btn",
          i(cls := "fa fa-sign-in"),
          I18n.t("topbar.login")
        )
    }

    nav(
      cls := "topbar",
      ul(
        cls := "actions",
        li(
          cls := "btn dropdown",
          i(cls := "fa fa-comments"),
          span(I18n.t("topbar.feedback")),
          i(cls := "fa fa-caret-down"),
          ul(
            cls := "subactions",
            li(
              onClick --> feedback,
              role := "link",
              title := I18n.t("topbar.feedback_tooltip"),
              cls := "btn",
              i(cls := "fa fa-gitter"),
              span(I18n.t("topbar.gitter"))
            ),
            li(
              onClick --> issue,
              role := "link",
              title := I18n.t("topbar.github_tooltip"),
              cls := "btn",
              i(cls := "fa fa-github"),
              span(I18n.t("topbar.github_issues"))
            )
          )
        ),
        li(
          cls := "btn",
          label(I18n.t("topbar.language_label")),
          select(
            value := props.language,
            cls := s"language-select ${if (props.isDarkTheme) "dark" else "light"}",
            onChange ==> { (e: ReactEventFromInput) =>
              val lang = e.target.value
              props.setLanguage(lang)
            },
            option(value := "en", "English")
          )
        ),
        profileButton
      )
    )
  }

  private val component = ScalaComponent
    .builder[TopBar]("TopBar")
    .render_P(render)
    .configure(Reusability.shouldComponentUpdate)
    .build

}
