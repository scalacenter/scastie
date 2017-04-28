package com.olegych.scastie
package client

import japgolly.scalajs.react._, vdom.all._

object SideBar {

  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend)]("SideBar")
      .render_P {
        case (state, backend) =>
          import backend._

          val toggleThemeLabel = if (state.isDarkTheme) "Light" else "Dark"

          val selectedIcon =
            if (state.isDarkTheme) "fa fa-sun-o"
            else "fa fa-moon-o"

          val themeButton =
            li(onClick ==> toggleTheme,
               role := "button",
               title := s"Select $toggleThemeLabel Theme (F2)",
               `class` := "btn")(
              i(`class` := s"fa $selectedIcon"),
              span(toggleThemeLabel)
            )

          val helpButton =
            li(onClick ==> toggleHelp,
               role := "button",
               title := "Show help Menu",
               `class` := "btn")(
              i(`class` := "fa fa-question-circle"),
              span("Help")
            )

          def selected(view: View) =
            if (view == state.view) TagMod(`class` := "selected")
            else EmptyVdom

          val profileButton =
            state.user match {
              case Some(user) =>
                li(onClick ==> setView2(View.CodeSnippets),
                   role := "link",
                   title := "Go to your code snippets",
                   `class` := "btn",
                   selected(View.CodeSnippets))(
                  img(src := user.avatar_url + "&s=32",
                      alt := "Your Github Avatar",
                      `class` := "avatar"),
                  span("Snippets")
                )
              case None => EmptyVdom
            }

          nav(`class` := "sidebar")(
            div(`class` := "actions-container")(
              div(`class` := "logo")(
                img(src := "/assets/public/img/icon-scastie.png"),
                h1("Scastie")
              ),
              ul(`class` := "actions-top")(
                EditorButton(state, backend),
                BuildSettingsButton(state, backend),
                profileButton
              ),
              ul(`class` := "actions-bottom")(
                themeButton,
                helpButton
              )
            )
          )
      }
      .build

  def apply(state: AppState, backend: AppBackend) = component((state, backend))
}
