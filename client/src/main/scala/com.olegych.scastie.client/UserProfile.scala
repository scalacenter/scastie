package com.olegych.scastie
package client

import api._
import autowire._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import japgolly.scalajs.react._, extra.router._, vdom.all._

object UserProfile {

  def apply(router: Option[RouterCtl[Page]], view: View) = component((router, view))

  class Backend(scope: BackendScope[(Option[RouterCtl[Page]], View), List[SnippetSummary]]){
    def loadProfile(): Callback = {
      Callback.future(
        ApiClient[Api]
          .fetchUserSnippets()
          .call()
          .map(summaries => scope.modState(_ => summaries))
      )
    }

    def delete(summary: SnippetSummary)(e: ReactEventI): Callback = {
      e.preventDefaultCB >>
      scope.modState(_.filterNot(_ == summary)) >>
      Callback.future(
        ApiClient[Api]
          .delete(summary.snippetId)
          .call()
          .map(_ => Callback(()))

      ) 
    }
  }

  private val component =
    ReactComponentB[(Option[RouterCtl[Page]], View)]("UserProfile")
      .initialState(List.empty[SnippetSummary])
      .backend(new Backend(_))
      .renderPS{
        case (scope, (maybeRouter, _), summaries) => {
          assert(maybeRouter.isDefined, "should not be able to access profile view from embedded")
          val router = maybeRouter.get

          div(`class` := "profile")(
            h1("Saved Code Snippets"),
            ul(
              summaries.groupBy(_.snippetId.base64UUID).map{ case (base64UUID, groupedSummaries) =>
                li(
                  p("/" + base64UUID),
                  ul(
                    groupedSummaries.sortBy(_.snippetId.user.flatMap(_.update)).map{summary =>
                      val page = Page.fromSnippetId(summary.snippetId)
                      val update = summary.snippetId.user.flatMap(_.update).getOrElse("")

                      li(router.setOnClick(page))(
                        p("Update: " + update),
                        router.link(page)(
                          pre(summary.summary)
                        ),
                        iconic.delete(
                          title := "Delete",
                          onClick ==> scope.backend.delete(summary)
                        )
                      )
                    }
                  )
                )
              }
            )
          )
        }
      }
      .componentWillReceiveProps{ delta =>
        val (_, currentView) = delta.currentProps
        val (_, nextView) = delta.nextProps

        if(currentView != View.UserProfile && nextView == View.UserProfile) {
          delta.$.backend.loadProfile()
        } else {
          Callback(())
        }
      }
      .componentWillMount(_.backend.loadProfile())
      .build
}
