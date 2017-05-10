package com.olegych.scastie
package client
package components

import api._
import autowire._

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}
import japgolly.scalajs.react.extra.router._

import scalajs.concurrent.JSExecutionContext.Implicits.queue

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
    ScalaComponent
      .builder[(Option[RouterCtl[Page]], AppState, AppBackend)](
        "CodeSnippets"
      )
      .initialState(List.empty[SnippetSummary])
      .backend(new Backend(_))
      .renderPS {
        case (scope, (maybeRouter, state, backend), summaries) => {
          assert(maybeRouter.isDefined,
                 "should not be able to access profile view from embedded")

          val router = maybeRouter.get

          val user = state.user.get

          val userAvatar = 
            div(clazz := "avatar")(
              img(src := user.avatar_url + "&s=70",
                  alt := "Your Github Avatar",
                  clazz := "image-button avatar")
            )

          val userName = user.name.getOrElse("")
          val userLogin = user.login

          val noSummaries =
            if (summaries.isEmpty) p("No saved snippets, yet!")
            else EmptyVdom

          div(clazz := "code-snippets-container")(
            userAvatar,
            h2(userName),
            div(clazz := "username")(
              i(clazz := "fa fa-github"),
              userLogin
            ),
            h2("Saved Code Snippets"),
            div(clazz := "snippets")(
              noSummaries,
              summaries
                .groupBy(_.snippetId.base64UUID)
                .map {
                  case (base64UUID, groupedSummaries) =>
                    div(clazz := "group", `key` := base64UUID)(
                      groupedSummaries
                        .sortBy(_.snippetId.user.flatMap(_.update))
                        .map {
                          summary =>
                            val page = Page.fromSnippetId(summary.snippetId)
                            val update = summary.snippetId.user
                              .flatMap(_.update)
                              .getOrElse("")
                            div(clazz := "snippet")(
                              ShareModal(
                                router,
                                summary.snippetId,
                                state.modalState.isShareModalClosed(summary.snippetId),
                                backend.toggleShare(None)
                              ),
                              div(clazz := "header", "/" + base64UUID)(
                                span(" - "),
                                div(clazz := "clear-mobile"),
                                span(clazz := "update", "Update: " + update),
                                div(clazz := "actions")(
                                  li(onClick ==> (_ => backend.toggleShare(Some(summary.snippetId))),
                                     clazz := "btn",
                                     title := "Share",
                                     role := "button")(
                                    i(clazz := "fa fa-share-alt")
                                  ),
                                  li(
                                    clazz := "btn",
                                    role := "button",
                                    title := "Delete",
                                    onClick ==> scope.backend.delete(summary)
                                  )(
                                    i(clazz := "fa fa-trash")
                                  )
                                )
                              ),
                              div(clazz := "codesnippet",
                                  role := "button",
                                  router.setOnClick(page))(
                                router.link(page)(
                                  pre(clazz := "code")(summary.summary)
                                )
                              )
                            )
                        }
                        .toTagMod
                    )
                }
                .toTagMod
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
