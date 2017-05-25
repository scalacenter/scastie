package com.olegych.scastie
package client
package components

import api._
import autowire._

import japgolly.scalajs.react._, vdom.all._, extra.router._
import japgolly.scalajs.react.component.builder.Lifecycle.RenderScope

import scalajs.concurrent.JSExecutionContext.Implicits.queue

final case class CodeSnippets(view: View,
                              user: User,
                              router: RouterCtl[Page],
                              isShareModalClosed: SnippetId => Boolean,
                              closeShareModal: Callback,
                              openShareModal: SnippetId => Callback) {
  @inline def render: VdomElement = CodeSnippets.component(this)
}

object CodeSnippets {
  private[CodeSnippets] class CodeSnippetsBackend(
      scope: BackendScope[CodeSnippets, List[SnippetSummary]]
  ) {

    def loadProfile(): Callback = {
      Callback.future(
        ApiClient[AutowireApi]
          .fetchUserSnippets()
          .call()
          .map(summaries => scope.modState(_ => summaries))
      )
    }

    def delete(summary: SnippetSummary): Callback = {
      val locally = scope.modState(_.filterNot(_ == summary))

      val remotely =
        Callback.future(
          ApiClient[AutowireApi]
            .delete(summary.snippetId)
            .call()
            .map(_ => Callback(()))
        )

      locally >> remotely
    }
  }

  private def renderSnippet(backend: CodeSnippetsBackend, props: CodeSnippets)(
      summary: SnippetSummary
  ): VdomElement = {
    val page = Page.fromSnippetId(summary.snippetId)
    val update = summary.snippetId.user
      .flatMap(_.update.map(_.toString))
      .getOrElse("")

    div(cls := "snippet")(
      ShareModal(
        router = props.router,
        snippetId = summary.snippetId,
        isClosed = props.isShareModalClosed(summary.snippetId),
        close = props.closeShareModal
      ).render,
      div(cls := "header", "/" + summary.snippetId.base64UUID)(
        span(" - "),
        div(cls := "clear-mobile"),
        span(cls := "update", "Update: " + update),
        div(cls := "actions")(
          li(onClick --> props.openShareModal(summary.snippetId),
             cls := "btn",
             title := "Share",
             role := "button")(
            i(cls := "fa fa-share-alt")
          ),
          li(
            cls := "btn",
            role := "button",
            title := "Delete",
            onClick --> backend.delete(summary)
          )(
            i(cls := "fa fa-trash")
          )
        )
      ),
      div(cls := "codesnippet",
          role := "button",
          props.router.setOnClick(page))(
        props.router.link(page)(
          pre(cls := "code")(summary.summary)
        )
      )
    )

  }

  private def render(
      scope: RenderScope[
        CodeSnippets,
        List[SnippetSummary],
        CodeSnippetsBackend
      ],
      props: CodeSnippets,
      summaries: List[SnippetSummary]
  ): VdomElement = {

    val userAvatar =
      div(cls := "avatar")(
        img(src := props.user.avatar_url + "&s=70",
            alt := "Your Github Avatar",
            cls := "image-button avatar")
      )

    val userName = props.user.name.getOrElse("")
    val userLogin = props.user.login

    val noSummaries =
      if (summaries.isEmpty) p("No saved snippets, yet!")
      else EmptyVdom

    div(cls := "code-snippets-container")(
      userAvatar,
      h2(userName),
      div(cls := "username")(
        i(cls := "fa fa-github"),
        userLogin
      ),
      h2("Saved Code Snippets"),
      div(cls := "snippets")(
        noSummaries,
        summaries
          .groupBy(_.snippetId.base64UUID)
          .map {
            case (base64UUID, groupedSummaries) =>
              div(cls := "group", key := base64UUID)(
                groupedSummaries
                  .sortBy(_.snippetId.user.flatMap(_.update))
                  .map(renderSnippet(scope.backend, props))
                  .toTagMod
              )
          }
          .toTagMod
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[CodeSnippets]("CodeSnippets")
      .initialState(List.empty[SnippetSummary])
      .backend(new CodeSnippetsBackend(_))
      .renderPS(render)
      .componentWillReceiveProps { delta =>
        val viewChangedToCodeSnippet =
          delta.currentProps.view != View.CodeSnippets &&
            delta.nextProps.view == View.CodeSnippets

        val loadProfile: Callback =
          delta.backend.loadProfile

        loadProfile.when_(viewChangedToCodeSnippet)
      }
      .componentWillMount(_.backend.loadProfile())
      .build
}
