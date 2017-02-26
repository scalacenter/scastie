package com.olegych.scastie
package client

import api._
import autowire._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

// import App._

import japgolly.scalajs.react._, extra.router._, vdom.all._

object UserProfile {

  def apply(router: Option[RouterCtl[Page]]) = component(router)

  class Backend(scope: BackendScope[Option[RouterCtl[Page]], List[SnippetSummary]]){
    def start(): Callback = {
      Callback.future(
        ApiClient[Api]
          .fetchUserSnippets()
          .call()
          .map(summaries => scope.modState(_ => summaries))
      )
    }
  }

  private val component =
    ReactComponentB[Option[RouterCtl[Page]]]("UserProfile")
      .initialState(List.empty[SnippetSummary])
      .backend(new Backend(_))
      .renderPS{
        case (scope, maybeRouter, summaries) => {
          assert(maybeRouter.isDefined, "should not be able to access profile view from embedded")
          val router = maybeRouter.get

          div(`class` := "profile")(
            h1("Saved Code Snippets"),
            ul(
              summaries.map{s =>
                val page = Page.fromSnippetId(s.snippetId)

                li(router.setOnClick(page))(
                  router.link(page)(
                    pre(s.summary)
                  )
                )
              }
            )
          )
        }
      }
      .componentWillMount(_.backend.start())
      .build
}
