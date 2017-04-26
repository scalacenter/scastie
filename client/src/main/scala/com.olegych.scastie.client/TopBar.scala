package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._
import org.scalajs.dom

object TopBar {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ScalaComponent.builder[(AppState, AppBackend)]("TopBar").render_P {
      case (state, backend) =>
        import backend._

        def openInNewTab(link: String): Callback = {
          Callback(
            dom.window.open(link, "_blank").focus()
          )
        }

        def feedback(e: ReactEventFromInput): Callback =
          openInNewTab("https://gitter.im/scalacenter/scastie")

        def issue(e: ReactEventFromInput): Callback =
          openInNewTab("https://github.com/scalacenter/scastie/issues/new")

        def selected(view: View) =
          if (view == state.view) TagMod(`class` := "selected") else EmptyVdom

        val logoutUrl = "/logout"

        def logout(e: ReactEventFromInput): Callback =
          backend.setView(View.Editor) >>
            Callback(dom.window.location.pathname = logoutUrl)

        def login(e: ReactEventFromInput): Callback =
          Callback(dom.window.location.pathname = "/login")

        val profileButton =
          state.user match {
            case Some(user) =>
              TagMod(
                li(onClick ==> setView2(View.CodeSnippets),
                   title := "Go to your code snippets",
                   `class` := "btn",
                   selected(View.CodeSnippets),
                   i(`class` := "fa fa-code"),
                   "Snippets"),
                li(onClick ==> logout, `class` := "btn")(
                  i(`class` := "fa fa-sign-out"),
                  "Logout"
                )
              )
            case None =>
              TagMod(
                li(onClick ==> login, `class` := "btn")(
                  i(`class` := "fa fa-sign-in"),
                  "Login"
                )
              )
          }

        val userAvatar = state.user match {
          case Some(user) =>
            img(src := user.avatar_url + "&s=30",
                alt := "Your Github Avatar",
                `class` := "avatar")
          case None => i(`class` := "fa fa-user-circle")
        }

        def topBarStyle = Seq(
          minWidth :=
            (if (state.dimensions.forcedDesktop)
               Dimensions.default.minWindowWidth
             else dom.window.innerWidth.toInt).px
        )

        val userName = state.user.map(_.login).getOrElse("Login")

        nav(`id` := "topbar", topBarStyle.toTagMod)(
          div(`class` := "logo")(
            img(src := "/assets/public/img/icon-scastie.png"),
            h1("Scastie")
          ),
          ul(`class` := "actions")(
            li(`class` := "btn dropdown")(
              i(`class` := "fa fa-comments"),
              span("Feedback"),
              i(`class` := "fa fa-caret-down"),
              ul(`class` := "subactions")(
                li(onClick ==> feedback,
                   title := "Open Gitter.im Chat to give us feedback",
                   `class` := "btn")(
                  i(`class` := "fa fa-gitter"),
                  "Scastie's gitter"
                ),
                li(onClick ==> issue,
                   title := "Create new issue on GitHub",
                   `class` := "btn")(
                  i(`class` := "fa fa-github"),
                  "Github issues"
                )
              )
            ),
            li(`class` := "btn dropdown")(
              userAvatar,
              span(userName),
              i(`class` := "fa fa-caret-down"),
              ul(`class` := "subactions")(
                profileButton
              )
            )
          )
        )

    }.build
}
