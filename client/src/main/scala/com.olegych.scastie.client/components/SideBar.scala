package com.olegych.scastie
package client
package components

import com.olegych.scastie.api._
import com.olegych.scastie.client.i18n.I18n

import japgolly.scalajs.react._
import vdom.all._
import extra._

import scala.scalajs.js
import js.annotation._

@JSImport("@resources/images/icon-scastie.png", JSImport.Default)
@js.native
object ScastieLogo extends js.Any

@JSImport("@resources/images/placeholder.png", JSImport.Default)
@js.native
object Placeholder extends js.Any

object Assets {
  def logo: String = ScastieLogo.asInstanceOf[String]
  def placeholder: String = Placeholder.asInstanceOf[String]
}

final case class SideBar(isDarkTheme: Boolean,
                         status: StatusState,
                         inputs: Inputs,
                         toggleTheme: Reusable[Callback],
                         view: StateSnapshot[View],
                         openHelpModal: Reusable[Callback],
                         openPrivacyPolicyModal: Reusable[Callback],
                         editorMode: EditorMode,
                         setEditorMode: EditorMode => Callback,
                         language: String) {
  @inline def render: VdomElement = SideBar.component(this)
}

object SideBar {

  implicit val reusability: Reusability[SideBar] =
    Reusability.derive[SideBar]

  private def render(props: SideBar): VdomElement = {
    val toggleThemeLabel =
      if (props.isDarkTheme) I18n.t("sidebar.theme_light")
      else I18n.t("sidebar.theme_dark")

    val theme = if (props.isDarkTheme) "light" else "dark"

    val selectedIcon =
      if (props.isDarkTheme) "fa fa-sun-o"
      else "fa fa-moon-o"

    val themeButton =
      li(onClick --> props.toggleTheme, role := "button", title := I18n.t(s"sidebar.theme_${theme}_tooltip"), cls := "btn")(
        i(cls := s"fa $selectedIcon"),
        span(toggleThemeLabel)
      )

    val privacyPolicyButton =
      li(onClick --> props.openPrivacyPolicyModal, role := "button", title := I18n.t("sidebar.privacy_policy_tooltip"), cls := "btn")(
        i(cls := "fa fa-user-secret"),
        span(I18n.t("sidebar.privacy_policy"))
      )

    val helpButton =
      li(onClick --> props.openHelpModal, role := "button", title := I18n.t("sidebar.help_tooltip"), cls := "btn")(
        i(cls := "fa fa-question-circle"),
        span(I18n.t("sidebar.help"))
      )

    val runnersStatusButton = {
      val (statusIcon, statusClass, statusLabel) =
        props.status.sbtRunnerCount match {
          case None =>
            ("fa-times-circle", "status-unknown", I18n.t("sidebar.status_unknown"))

          case Some(0) =>
            ("fa-times-circle", "status-down", I18n.t("sidebar.status_down"))

          case Some(_) =>
            ("fa-check-circle", "status-up", I18n.t("sidebar.status_up"))
        }

      li(onClick --> props.view.setState(View.Status), role := "button", title := I18n.t("sidebar.status_tooltip"), cls := s"btn $statusClass")(
        i(cls := s"fa $statusIcon"),
        span(statusLabel)
      )
    }

    val editorButton = ViewToggleButton(
      currentView = props.view,
      forView = View.Editor,
      buttonTitle = I18n.t("sidebar.editor"),
      faIcon = "fa-edit",
      onClick = reusableEmpty
    ).render

    val buildSettingsButton = ViewToggleButton(
      currentView = props.view,
      forView = View.BuildSettings,
      buttonTitle = I18n.t("sidebar.build_settings"),
      faIcon = "fa-gear",
      onClick = reusableEmpty
    ).render

    val editorModeSelector =
      li(
        cls := "btn",
        i(cls := "fa fa-keyboard-o"),
        select(
          value := props.editorMode.toString,
          cls := s"editor-mode-select ${if (props.isDarkTheme) "dark" else "light"}",
          onChange ==> { (e: ReactEventFromInput) =>
            val mode = e.target.value match {
              case "Default" => Default
              case "Vim"     => Vim
              case "Emacs"   => Emacs
              case _         => Default
            }
            props.setEditorMode(mode)
          },
          option(value := "Default", "Default"),
          option(value := "Vim", "Vim"),
          option(value := "Emacs", "Emacs")
        )
      )

    nav(cls := "sidebar")(
      div(cls := "actions-container")(
        div(cls := "logo")(
          img(src := Assets.logo),
          h1("Scastie")
        ),
        ul(cls := "actions-top")(
          editorButton,
          buildSettingsButton
        ),
        ul(cls := "actions-bottom")(
          editorModeSelector,
          themeButton,
          privacyPolicyButton,
          helpButton,
          runnersStatusButton
        )
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[SideBar]("SideBar")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
