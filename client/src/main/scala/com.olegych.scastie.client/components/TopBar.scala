package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

import org.scalajs.dom

object TopBar {

  def apply(state: AppState, backend: AppBackend) = component((state, backend))

  private val component =
    ScalaComponent
      .builder[(AppState, AppBackend)]("TopBar")
      .render_P {
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

          val logoutUrl = "/logout"

          def logout(e: ReactEventFromInput): Callback =
            backend.setView(View.Editor) >>
              Callback(dom.window.location.pathname = logoutUrl)

          def login(e: ReactEventFromInput): Callback =
            Callback(dom.window.location.pathname = "/login")

          def selected(view: View) =
            if (view == state.view) TagMod(clazz := "selected")
            else EmptyVdom

          val profileButton =
            state.user match {
              case Some(user) =>
                li(clazz := "btn dropdown")(
                  img(
                    src := user.avatar_url + "&s=30",
                    alt := "Your Github Avatar",
                    clazz := "avatar"),
                  span(user.login),
                  i(clazz := "fa fa-caret-down"),
                  ul(clazz := "subactions")(
                    li(onClick ==> setView2(View.CodeSnippets),
                       role := "link",
                       title := "Go to your code snippets",
                       clazz := "btn",
                       selected(View.CodeSnippets)(
                      i(clazz := "fa fa-code"),
                      "Snippets"),
                    ),
                    li(role := "link", onClick ==> logout, clazz := "btn")(
                      i(clazz := "fa fa-sign-out"),
                      "Logout"
                    )
                  )
                )

              case None =>
                li(role := "link", onClick ==> login, clazz := "btn")(
                  i(clazz := "fa fa-sign-in"),
                  "Login"
                )
            }

          nav(clazz := "topbar")(
            ul(clazz := "actions")(
              li(clazz := "btn dropdown")(
                i(clazz := "fa fa-comments"),
                span("Feedback"),
                i(clazz := "fa fa-caret-down"),
                ul(clazz := "subactions")(
                  li(onClick ==> feedback,
                     role := "link",
                     title := "Open Gitter.im Chat to give us feedback",
                     clazz := "btn")(
                    i(clazz := "fa fa-gitter"),
                    span("Scastie's gitter")
                  ),
                  li(onClick ==> issue,
                     role := "link",
                     title := "Create new issue on GitHub",
                     clazz := "btn")(
                    i(clazz := "fa fa-github"),
                    span("Github issues")
                  )
                )
              ),
              profileButton
            )
          )

      }
      .build
}
