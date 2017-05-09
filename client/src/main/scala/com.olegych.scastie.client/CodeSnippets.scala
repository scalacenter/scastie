package com.olegych.scastie
package client

import api._
import autowire._

import japgolly.scalajs.react._, vdom.all._, extra.router._

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

          val noSummaries =
            if (summaries.isEmpty) p("No saved snippets, yet!")
            else EmptyVdom

          div(`class` := "code-snippets-container")(
            div(`class` := "snippets")(
              noSummaries,
              summaries
                .groupBy(_.snippetId.base64UUID)
                .map {
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
                                     role := "button",
                                     onClick ==> backend
                                       .toggleShare(Some(summary.snippetId)))(
                                    i(`class` := "fa fa-share-alt")
                                  ),
                                  li(
                                    `class` := "btn",
                                    role := "button",
                                    title := "Delete",
                                    onClick ==> scope.backend.delete(summary)
                                  )(
                                    i(`class` := "fa fa-trash")
                                  )
                                )
                              ),
                              div(`class` := "codesnippet",
                                  role := "button",
                                  router.setOnClick(page))(
                                router.link(page)(
                                  pre(`class` := "code")(summary.summary)
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
