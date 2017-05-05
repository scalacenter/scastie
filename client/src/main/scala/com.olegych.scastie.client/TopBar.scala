package com.olegych.scastie.client

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._
import org.scalajs.dom

object TopBar {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ScalaComponent.builder[(AppState, AppBackend)]("TopBar").render_P {
      case (state, backend) =>

        def openInNewTab(link: String): Callback = {
          Callback(
            dom.window.open(link, "_blank").focus()
          )
        }

        def feedback(e: ReactEventFromInput): Callback =
          openInNewTab("https://gitter.im/scalacenter/scastie")

        def issue(e: ReactEventFromInput): Callback =
          openInNewTab("https://github.com/scalacenter/scastie/issues/new")

        val logoutUrl = "/logout"

        def logout(e: ReactEventFromInput): Callback =
          backend.setView(View.Editor) >>
            Callback(dom.window.location.pathname = logoutUrl)

        def login(e: ReactEventFromInput): Callback =
          Callback(dom.window.location.pathname = "/login")


        val profileButton =
          state.user match {
            case Some(user) =>
              li(
                role := "link",
                onClick ==> logout,
                `class` := "btn")(
                i(`class` := "fa fa-sign-out"),
                "Logout"
              )

            case None =>
              li(role := "link", onClick ==> login, `class` := "btn")(
                i(`class` := "fa fa-sign-in"),
                "Login"
              )
          }


        nav(`class` := "topbar")(
          ul(`class` := "actions")(
            li(onClick ==> feedback,
               role := "link",
               title := "Open Gitter.im Chat to give us feedback",
               `class` := "btn")(
              i(`class` := "fa fa-gitter"),
              "Scastie's gitter"
            ),
            li(onClick ==> issue,
               role := "link",
               title := "Create new issue on GitHub",
               `class` := "btn")(
              i(`class` := "fa fa-github"),
              "Github issues"
            ),
            profileButton
          )
        )

    }.build
}
