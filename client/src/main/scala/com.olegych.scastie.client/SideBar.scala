package com.olegych.scastie
package client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._
import org.scalajs.dom

object SideBar {

  private val component =
    ReactComponentB[(AppState, AppBackend)]("SideBar").render_P {
      case (state, backend) =>
        import backend._
        import dom.window._
        import state.dimensions._

        val toggleThemeLabel = if (state.isDarkTheme) "Light" else "Dark"

        val selectedIcon =
          if (state.isDarkTheme) "fa fa-sun-o"
          else "fa fa-moon-o"

        val displayMobile =
          if (state.dimensions.forcedDesktop) display.block
          else display.none

        val mobileButton =
          li(onClick ==> backend.toggleMobile,
             title := "Go back to Mobile Version",
             `class` := "btn",
             displayMobile)(
            i(`class` := s"fa fa-mobile"),
            span("Mobile")
          )

        val themeButton =
          li(onClick ==> toggleTheme,
             title := s"Select $toggleThemeLabel Theme (F2)",
             `class` := "btn")(
            i(`class` := s"fa $selectedIcon"),
            span(toggleThemeLabel)
          )

        val helpButton =
          li(onClick ==> toggleHelp,
             title := "Show help Menu",
             `class` := "btn")(
            i(`class` := "fa fa-question-circle"),
            span("Help")
          )

        val buttonsTop: Seq[TagMod] = Seq(EditorButton(state, backend),
                                          BuildSettingsButton(state, backend))

        val buttonsBottom: Seq[TagMod] =
          Seq(mobileButton, themeButton, helpButton)

        def sidebarStyle: TagMod =
          TagMod(
            height := "5000px"
          )

        def actionsContainerStyle: TagMod =
          TagMod(
            if (forcedDesktop)
              minHeight := Dimensions.default.minWindowHeight.px
            else height := innerHeight.toInt.px
          )

        nav(`id` := "sidebar", sidebarStyle)(
          div(`class` := "actions-container", actionsContainerStyle)(
            div(`class` := "logo")(
              img(src := "/assets/public/img/icon-scastie.png"),
              h1("Scastie")
            ),
            ul(`id` := "actions-top", `class` := "actions")(
              buttonsTop
            ),
            ul(`id` := "actions-bottom", `class` := "actions bottom")(
              buttonsBottom
            )
          )
        )
    }.build

  def apply(state: AppState, backend: AppBackend) = component((state, backend))
}
