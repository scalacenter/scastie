package com.olegych.scastie
package client

import com.olegych.scastie.client.DefaultSizes._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._
import org.scalajs.dom.window._

object SideBar {

  private val component =
    ReactComponentB[(AppState, AppBackend)]("SideBar").render_P {
      case (state, backend) =>
        import backend._

        def currentView = state.view

        val toggleThemeLabel = if (state.isDarkTheme) "Light" else "Dark"

        val selectedIcon =
          if (state.isDarkTheme) "fa fa-sun-o"
          else "fa fa-moon-o"

        val themeButton =
          li(onClick ==> toggleTheme,
            title := s"Select $toggleThemeLabel Theme (F2)",
            `class` := "btn")(
            i(`class` := s"fa $selectedIcon"),
            toggleThemeLabel
          )

        val helpButton =
          li(onClick ==> toggleHelp,
            title := "Show help Menu",
            `class` := "btn")(
            i(`class` := "fa fa-question-circle"),
            "Help"
          )

        val buttonsTop: Seq[TagMod] = Seq(EditorButton(state, backend), BuildSettingsButton(state, backend))

        val buttonsBottom: Seq[TagMod] = Seq(themeButton, helpButton)

        def actionsContainerStyle: TagMod = TagMod(
          height := s"${if (innerHeight < sideBarMinHeight) sideBarMinHeight else innerHeight}px")

        nav(`id` := "sidebar")(
          div(`class` := "actions-container", actionsContainerStyle)(
            a(`class` := "logo", href := "#")(
              img(src := "/assets/public/img/icon-scastie.png"),
              h1("Scastie")
            ),
            ul(`class` := "actions")(
              buttonsTop
            ),
            ul(`class` := "actions bottom")(
              buttonsBottom
            )
          )
        )
    }.build

  def apply(state: AppState, backend: AppBackend) = component((state, backend))
}
