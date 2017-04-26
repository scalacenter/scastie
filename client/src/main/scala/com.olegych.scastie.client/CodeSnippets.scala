package com.olegych.scastie
package client

import api._
import autowire._

import scalajs.concurrent.JSExecutionContext.Implicits.queue
import japgolly.scalajs.react._
import extra.router._
import vdom.all._
import org.scalajs.dom.window._

object CodeSnippets {

  def apply(router: Option[RouterCtl[Page]],
            state: AppState,
            backend: AppBackend) =
    component((router, state, backend))

  class Backend(
      scope: BackendScope[(Option[RouterCtl[Page]], AppState, AppBackend),
                          List[SnippetSummary]]
  ) {
    def loadProfile(): Callback = {
      Callback.future(
        ApiClient[AutowireApi]
          .fetchUserSnippets()
          .call()
          .map(summaries => scope.modState(_ => summaries))
      )
    }

    def delete(summary: SnippetSummary)(e: ReactEventFromInput): Callback = {
      e.preventDefaultCB >>
        scope.modState(_.filterNot(_ == summary)) >>
        Callback.future(
          ApiClient[AutowireApi]
            .delete(summary.snippetId)
            .call()
            .map(_ => Callback(()))
        )
    }
  }

  private val component =
    ScalaComponent.builder[(Option[RouterCtl[Page]], AppState, AppBackend)](
      "CodeSnippets"
    ).initialState(List.empty[SnippetSummary])
      .backend(new Backend(_))
      .renderPS {
        case (scope, (maybeRouter, state, backend), summaries) => {

          import state.dimensions._

          assert(maybeRouter.isDefined,
                 "should not be able to access profile view from embedded")
          val router = maybeRouter.get

          val (userAvatar, userName, userLogin) = state.user match {
            case Some(user) =>
              (div(`class` := "avatar")(
                 img(src := user.avatar_url + "&s=70",
                     alt := "Your Github Avatar",
                     `class` := "image-button avatar")
               ),
               user.name,
               user.login)
            case None =>
              (i(`class` := "fa fa-user-circle"),
               Some("User name"),
               "Github user")
          }

          def containerStyle: TagMod =
            if (forcedDesktop) height := (innerHeight - topBarHeight).px
            else EmptyVdom

          div(`id` := "code-snippets-container", containerStyle)(
            userAvatar,
            userName.map(u => h2(u)).getOrElse(EmptyVdom),
            div(`class` := "nickname")(
              i(`class` := "fa fa-github"),
              userLogin
            ),
            h2("Saved Code Snippets"),
            div(`id` := "snippets")(
              summaries.groupBy(_.snippetId.base64UUID).map {
                case (base64UUID, groupedSummaries) =>
                  div(`class` := "group", `key` := base64UUID)(
                    groupedSummaries
                      .sortBy(_.snippetId.user.flatMap(_.update))
                      .map {
                        summary =>
                          val page = Page.fromSnippetId(summary.snippetId)
                          val update = summary.snippetId.user
                            .flatMap(_.update)
                            .getOrElse("")
                          div(`class` := "snippet")(
                            div(`class` := "header", "/" + base64UUID)(
                              span(" - "),
                              div(`class` := "clear-mobile"),
                              span(`class` := "update", "Update: " + update),
                              div(`class` := "actions")(
                                li(`class` := "btn",
                                   title := "Share",
                                   onClick ==> backend
                                     .toggleShare(Some(summary.snippetId)))(
                                  i(`class` := "fa fa-share-alt")
                                ),
                                li(`class` := "btn",
                                   title := "Delete",
                                   onClick ==> scope.backend.delete(summary))(
                                  i(`class` := "fa fa-trash")
                                )
                              )
                            ),
                            div(`class` := "codesnippet",
                                router.setOnClick(page))(
                              router.link(page)(
                                pre(`class` := "code")(summary.summary)
                              )
                            )
                          )
                      }.toTagMod
                  )
              }.toTagMod
            )
          )
        }
      }
      .componentWillReceiveProps { delta =>
        val (_, currentAppState, _) = delta.currentProps
        val (_, nextAppState, _) = delta.nextProps

        if (currentAppState.view != View.CodeSnippets && nextAppState.view == View.CodeSnippets) {
          delta.backend.loadProfile()
        } else {
          Callback(())
        }
      }
      .componentWillMount(_.backend.loadProfile())
      .build
}
