package com.olegych.scastie
package client

import api._
import autowire._

import scalajs.concurrent.JSExecutionContext.Implicits.queue
import japgolly.scalajs.react._
import extra.router._
import vdom.all._

object CodeSnippets {

  def apply(router: Option[RouterCtl[Page]], state: AppState) =
    component((router, state))

  class Backend(
      scope: BackendScope[(Option[RouterCtl[Page]], AppState),
                          List[SnippetSummary]]) {
    def loadProfile(): Callback = {
      Callback.future(
        ApiClient[AutowireApi]
          .fetchUserSnippets()
          .call()
          .map(summaries => scope.modState(_ => summaries))
      )
    }

    def delete(summary: SnippetSummary)(e: ReactEventI): Callback = {
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
    ReactComponentB[(Option[RouterCtl[Page]], AppState)]("CodeSnippets")
      .initialState(List.empty[SnippetSummary])
      .backend(new Backend(_))
      .renderPS {
        case (scope, (maybeRouter, state), summaries) => {
          assert(maybeRouter.isDefined,
                 "should not be able to access profile view from embedded")
          val router = maybeRouter.get

          val (userAvatar, userName, userLogin) = state.user match {
            case Some(user) =>
              (div(`class` := "avatar")(
                img(src := user.avatar_url + "&s=70",
                  alt := "Your Github Avatar",
                  `class` := "image-button avatar")
              ), user.name, user.login)
            case None => (i(`class` := "fa fa-user-circle"), Some("User name"), "Github user")
          }

          div(`id` := "code-snippets-container")(
            userAvatar,
            h2(userName),
            div(`class` := "nickname")(
              i(`class` := "fa fa-github"),
              userLogin
            ),

            h2("Saved Code Snippets"),
            div(`id` := "snippets")(
              summaries.groupBy(_.snippetId.base64UUID).map {
                case (base64UUID, groupedSummaries) =>
                  div(`class` := "group")(
                    groupedSummaries
                      .sortBy(_.snippetId.user.flatMap(_.update))
                      .map {
                        summary =>
                          val page = Page.fromSnippetId(summary.snippetId)
                          val update = summary.snippetId.user
                            .flatMap(_.update)
                            .getOrElse("")
                          div(`class` := "snippet")(
                            div(`class` := "header", "/" + base64UUID + " - ")(
                              span(`class` := "update", "Update: " + update),
                              div(`class` := "actions")(
                                a(href := "#",
                                  title := "Share")(
                                  i(`class` := "fa fa-share-alt")
                                ),
                                a(href := "#",
                                  title := "Delete",
                                  onClick ==> scope.backend.delete(summary))(
                                  i(`class` := "fa fa-trash")
                                )
                              )
                            ),
                            div(`class` := "codesnippet", router.setOnClick(page))(
                              router.link(page)(
                                pre(`class` := "code")(summary.summary)
                              )
                            )
                          )
                      }
                  )
              }
            )
          )
        }
      }
      .componentWillReceiveProps { delta =>
        val (_, currentAppState) = delta.currentProps
        val (_, nextAppState) = delta.nextProps

        if (currentAppState.view != View.CodeSnippets && nextAppState.view == View.CodeSnippets) {
          delta.$.backend.loadProfile()
        } else {
          Callback(())
        }
      }
      .componentWillMount(_.backend.loadProfile())
      .build
}
