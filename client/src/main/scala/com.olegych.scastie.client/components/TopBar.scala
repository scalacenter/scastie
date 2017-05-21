package com.olegych.scastie
package client
package components

import com.olegych.scastie.api.User
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.{Reusability, StateSnapshot}
import japgolly.scalajs.react.vdom.all._
import org.scalajs.dom
import ApiReusability._

final case class TopBar(view: StateSnapshot[View], user: Option[User]) {
  @inline def render: VdomElement = TopBar.component(this)
}

object TopBar {

  implicit val reusability: Reusability[TopBar] =
    Reusability.caseClass

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

    def login: Callback =
      Callback(dom.window.location.pathname = "/login")

    def selected(view: View) =
      (cls := "selected").when(view == props.view.value)

    val profileButton =
      props.user match {
        case Some(user) =>
          li(cls := "btn dropdown",
            img(src := user.avatar_url + "&s=30",
              alt := "Your Github Avatar",
              cls := "avatar"),
            span(user.login),
            i(cls := "fa fa-caret-down"),
            ul(cls := "subactions",
              li(onClick --> props.view.setState(View.CodeSnippets),
                role := "link",
                title := "Go to your code snippets",
                cls := "btn",
                selected(View.CodeSnippets)(
                  i(cls := "fa fa-code"),
                  "Snippets"
                )),
              li(role := "link", onClick --> logout, cls := "btn",
                i(cls := "fa fa-sign-out"),
                "Logout"
              )
            )
          )

        case None =>
          li(role := "link", onClick --> login, cls := "btn",
            i(cls := "fa fa-sign-in"),
            "Login"
          )
      }

    nav(cls := "topbar",
      ul(cls := "actions",
        li(cls := "btn dropdown",
          i(cls := "fa fa-comments"),
          span("Feedback"),
          i(cls := "fa fa-caret-down"),
          ul(cls := "subactions",
            li(onClick --> feedback,
              role := "link",
              title := "Open Gitter.im Chat to give us feedback",
              cls := "btn",
              i(cls := "fa fa-gitter"),
              span("Scastie's gitter")
            ),
            li(onClick --> issue,
              role := "link",
              title := "Create new issue on GitHub",
              cls := "btn",
              i(cls := "fa fa-github"),
              span("Github issues")
            )
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
