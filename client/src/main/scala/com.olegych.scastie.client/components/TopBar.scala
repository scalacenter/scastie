package com.olegych.scastie
package client
package components

import api.User

import japgolly.scalajs.react._, vdom.all._, extra._

import org.scalajs.dom

final case class TopBar(view: StateSnapshot[View], user: Option[User], openLoginModal: Reusable[Callback]) {
  @inline def render: VdomElement = TopBar.component(this)
}

object TopBar {

  implicit val reusability: Reusability[TopBar] =
    Reusability.derive[TopBar]

  private def render(props: TopBar): VdomElement = {
    def openInNewTab(link: String): Callback =
      Callback {
        dom.window.open(link, "_blank").focus()
      }

    def feedback: Callback =
      openInNewTab("https://gitter.im/scalacenter/scastie")

    def issue: Callback =
      openInNewTab("https://github.com/scalacenter/scastie/issues/new")

    val logoutUrl = "/logout"

    def logout: Callback =
      props.view.setState(View.Editor) >>
        Callback(dom.window.location.pathname = logoutUrl)

    val profileButton =
      props.user match {
        case Some(user) =>
          li(
            cls := "btn dropdown",
            img(src := user.avatar_url + "&s=30", alt := "Your Github Avatar", cls := "avatar"),
            span(user.login),
            i(cls := "fa fa-caret-down"),
            ul(
              cls := "subactions",
              li(
                onClick --> props.view.setState(View.CodeSnippets),
                role := "link",
                title := "Go to your code snippets",
                cls := "btn",
                (cls := "selected").when(View.CodeSnippets == props.view.value)
              )(
                i(cls := "fa fa-code"),
                "Snippets"
              ),
              li(role := "link", onClick --> logout, cls := "btn", i(cls := "fa fa-sign-out"), "Logout")
            )
          )

        case None =>
          li(role := "link", onClick --> props.openLoginModal, cls := "btn", i(cls := "fa fa-sign-in"), "Login")
      }

    nav(
      cls := "topbar",
      ul(
        cls := "actions",
        li(
          cls := "btn dropdown",
          i(cls := "fa fa-comments"),
          span("Feedback"),
          i(cls := "fa fa-caret-down"),
          ul(
            cls := "subactions",
            li(onClick --> feedback,
               role := "link",
               title := "Open Gitter.im Chat to give us feedback",
               cls := "btn",
               i(cls := "fa fa-gitter"),
               span("Scastie's gitter")),
            li(onClick --> issue,
               role := "link",
               title := "Create new issue on GitHub",
               cls := "btn",
               i(cls := "fa fa-github"),
               span("Github issues"))
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
