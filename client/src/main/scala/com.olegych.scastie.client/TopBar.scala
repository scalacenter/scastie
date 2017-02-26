package com.olegych.scastie.client

import App._

import japgolly.scalajs.react._, vdom.all._

import org.scalajs.dom

object TopBar {

  def apply(state: State, backend: Backend) = component((state, backend))

  private val component =
    ReactComponentB[(State, Backend)]("TopBar").render_P {
      case (state, backend) =>
      import backend._

        val theme = if (state.isDarkTheme) "dark" else "light"

        val toggleThemeLabel = if (state.isDarkTheme) "Light" else "Dark"

        val selectedTheme =
          if (state.isDarkTheme) iconic.sun
          else iconic.moon

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

        val profileButton = 
          state.user match {
            case Some(user) =>
              li(onClick ==> setView2(View.UserProfile),
                 title := "Open Profile View",
                 selected(View.UserProfile),
                 `class` := "button")(
                img(src := user.avatar_url + "&s=35",
                    alt := "Your Github Avatar",
                    `class` := "image-button avatar"),
                p("Profile")
              )
            case None => EmptyTag
          }

        nav(`class` := s"topbar $theme")(
          ul(
            profileButton,
            li(onClick ==> backend.toggleTheme,
               title := s"Select $toggleThemeLabel Theme (F2)",
               `class` := "button")(
              selectedTheme,
              p("Theme")
            ),
            li(onClick ==> feedback,
                 title := "Open Gitter.im Chat to give us feedback",
                 `class` := "button")(
              iconic.chat,
              p("Feedback")
            ),
            li(onClick ==> issue,
               title := "Create new issue on GitHub",
               `class` := "button")(
              iconic.bug,
              p("Issue")
            ),
            li(onClick ==> showHelp,
               title := "Show help Menu",
               `class` := "button")(
              iconic.questionMark,
              p("Help")
            )
          )
        )
    }.build
}
