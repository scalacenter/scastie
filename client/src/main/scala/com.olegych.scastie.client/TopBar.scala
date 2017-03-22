package com.olegych.scastie.client

import japgolly.scalajs.react._, vdom.all._

import org.scalajs.dom
import org.scalajs.dom.window._

object TopBar {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ReactComponentB[(AppState, AppBackend)]("TopBar").render_P {
      case (state, backend) =>
        import backend._

        def openInNewTab(link: String): Callback = {
          Callback(
            dom.window.open(link, "_blank").focus()
          )
        }

        def feedback(e: ReactEventI): Callback =
          openInNewTab("https://gitter.im/scalacenter/scastie")

        def issue(e: ReactEventI): Callback =
          openInNewTab("https://github.com/scalacenter/scastie/issues/new")

        def selected(view: View) =
          if (view == state.view) TagMod(`class` := "selected") else EmptyTag

        val logoutUrl = "/logout"

        def logout(e: ReactEventI): Callback =
          backend.setView(View.Editor) >>
            Callback(dom.window.location.pathname = logoutUrl)

        def login(e: ReactEventI): Callback =
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
                  "Snippets"
                ),
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

        val topBarMinWidth = 500
        val sideBarWidth = 149

        def actionsTopBarStyle: TagMod =
          if (innerWidth < topBarMinWidth) TagMod(left := sideBarWidth) else EmptyTag

        nav(`id` := "topbar")(
          ul(`class` := "actions", actionsTopBarStyle)(
            li(`class` := "btn dropdown")(
              i(`class` := "fa fa-comments"),
              "Feedback",
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
              i(`class` := "fa fa-user-circle"),
              "Login",
              i(`class` := "fa fa-caret-down"),
              ul(`class` := "subactions")(
                profileButton
              )
            )
          )
        )

    }.build
}
