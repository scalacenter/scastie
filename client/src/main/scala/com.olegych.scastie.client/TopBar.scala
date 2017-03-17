package com.olegych.scastie.client

import japgolly.scalajs.react._, vdom.all._

import org.scalajs.dom

object TopBar {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ReactComponentB[(AppState, AppBackend)]("TopBar").render_P {
      case (state, backend) =>
        import backend._

        val theme = if (state.isDarkTheme) "dark" else "light"

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
                li(onClick ==> setView2(View.UserProfile),
                   title := "Open Profile View",
                   selected(View.UserProfile),
                   `class` := "btn")(
                  img(src := user.avatar_url + "&s=35",
                      alt := "Your Github Avatar",
                      `class` := "image-button avatar"),
                  p("Snippets")
                ),
                li(onClick ==> logout, `class` := "btn")(
                  iconic.accountLogout,
                  p("Logout")
                )
              )
            case None =>
              TagMod(
                li(onClick ==> login, `class` := "btn")(
                  iconic.accountLogin,
                  p("Login")
                )
              )
          }

        nav(`id` := s"topbar")(
          ul(
            profileButton,
            li(onClick ==> feedback,
               title := "Open Gitter.im Chat to give us feedback",
               `class` := "btn")(
              iconic.chat,
              p("Feedback")
            ),
            li(onClick ==> issue,
               title := "Create new issue on GitHub",
               `class` := "btn")(
              iconic.bug,
              p("Issue")
            )
          )
        )
    }.build
}
