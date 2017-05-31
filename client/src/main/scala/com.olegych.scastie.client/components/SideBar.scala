package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._, vdom.all._, extra._

final case class SideBar(isDarkTheme: Boolean,
                         status: StatusState,
                         toggleTheme: Callback,
                         view: StateSnapshot[View],
                         openHelpModal: Callback) {
  @inline def render: VdomElement = SideBar.component(this)
}

object SideBar {
  private def render(props: SideBar): VdomElement = {
    val toggleThemeLabel =
      if (props.isDarkTheme) "Light"
      else "Dark"

    val selectedIcon =
      if (props.isDarkTheme) "fa fa-sun-o"
      else "fa fa-moon-o"

    val themeButton =
      li(onClick --> props.toggleTheme,
         role := "button",
         title := s"Select $toggleThemeLabel Theme (F2)",
         cls := "btn")(
        i(cls := s"fa $selectedIcon"),
        span(toggleThemeLabel)
      )

    val helpButton =
      li(onClick --> props.openHelpModal,
         role := "button",
         title := "Show help Menu",
         cls := "btn")(
        i(cls := "fa fa-question-circle"),
        span("Help")
      )

    val statusButton = {
      val (statusIcon, statusClass, statusLabel) =
        props.status.runnerCount match {
          case None => ("fa-times-circle", "status-unknown", "Unknown")
          case Some(0) => ("fa-times-circle", "status-down", "Down")
          case Some(_) => ("fa-check-circle", "status-up", "Up")
        }

      li(onClick --> props.view.setState(View.Status),
         role := "button",
         title := "Show runners status",
         cls := s"btn $statusClass")(
        i(cls := s"fa $statusIcon"),
        span(statusLabel)
      )
    }

    val editorButton = ViewToggleButton(
      currentView = props.view,
      forView = View.Editor,
      buttonTitle = "Editor",
      faIcon = "fa-edit"
    ).render

    val buildSettingsButton = ViewToggleButton(
      currentView = props.view,
      forView = View.BuildSettings,
      buttonTitle = "Build Settings",
      faIcon = "fa-gear"
    ).render

    nav(cls := "sidebar")(
      div(cls := "actions-container")(
        div(cls := "logo")(
          img(src := "/assets/public/img/icon-scastie.png"),
          h1("Scastie")
        ),
        ul(cls := "actions-top")(
          editorButton,
          buildSettingsButton
        ),
        ul(cls := "actions-bottom")(
          themeButton,
          helpButton,
          statusButton
        )
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[SideBar]("SideBar")
      .render_P(render)
      .build
}
