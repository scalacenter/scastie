package com.olegych.scastie.client.components

import com.olegych.scastie.api._
import com.olegych.scastie.client.Page
import com.olegych.scastie.client.View
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.builder.Lifecycle.RenderScope

import scala.concurrent.Future

import vdom.all._
import extra.router._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

final case class CodeSnippets(
    view: View,
    user: User,
    router: RouterCtl[Page],
    isDarkTheme: Boolean,
    isShareModalClosed: SnippetId ~=> Boolean,
    closeShareModal: Reusable[Callback],
    openShareModal: SnippetId ~=> Callback,
    loadProfile: Reusable[Future[List[SnippetSummary]]],
    deleteSnippet: SnippetId ~=> Future[Boolean]
) {

  @inline def render: VdomElement = CodeSnippets.component(this)
}

object CodeSnippets {
  implicit val reusability: Reusability[CodeSnippets] =
    Reusability.derive[CodeSnippets]

  private[CodeSnippets] class CodeSnippetsBackend(
      scope: BackendScope[CodeSnippets, List[SnippetSummary]]
  ) {

    def loadProfile0(): Callback = {
      scope.props.flatMap(
        props =>
          Callback.future(
            props.loadProfile.map(
              _.map(summaries => scope.modState(_ => summaries))
            )
        )
      )
    }

    def deleteSnippet0(summary: SnippetSummary): Callback = {
      scope.props.flatMap(
        props =>
          Callback.future(
            props
              .deleteSnippet(summary.snippetId)
              .map(
                deleted => scope.modState(_.filterNot(_ == summary)).when_(deleted)
              )
        )
      )
    }
  }

  private def renderSnippet(backend: CodeSnippetsBackend, props: CodeSnippets)(
      summary: SnippetSummary
  ): VdomElement = {

    val page = Page.fromSnippetId(summary.snippetId)
    val update = summary.snippetId.user.map(_.update.toString).getOrElse("")

    val snippetUrl =
      props.router.urlFor(Page.fromSnippetId(summary.snippetId)).value

    div(cls := "snippet")(
      CopyModal(
        isDarkTheme = props.isDarkTheme,
        title = "Share your Code Snippet",
        subtitle = "Copy and share your code snippet's URL:",
        modalId = "share-modal-" + summary.snippetId.url.replace(".", "-"),
        content = snippetUrl,
        isClosed = props.isShareModalClosed(summary.snippetId),
        close = props.closeShareModal
      ).render,
      div(cls := "header", "/" + summary.snippetId.base64UUID)(
        span(" - "),
        div(cls := "clear-mobile"),
        span(cls := "update", "Update: " + update),
        div(cls := "actions")(
          li(onClick --> props.openShareModal(summary.snippetId), cls := "btn", title := "Share", role := "button")(
            i(cls := "fa fa-share-alt")
          ),
          li(
            cls := "btn",
            role := "button",
            title := "Delete",
            onClick --> backend.deleteSnippet0(summary)
          )(
            i(cls := "fa fa-trash")
          )
        )
      ),
      div(cls := "codesnippet", role := "button", props.router.setOnClick(page))(
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
        img(src := props.user.avatar_url + "&s=70", alt := "Your Github Avatar", cls := "image-button avatar")
      )

    val userName = props.user.name.getOrElse("")
    val userLogin = props.user.login

    val noSummaries =
      if (summaries.isEmpty) p("No saved snippets, yet!")
      else EmptyVdom

    def sortSnippets(xs: List[SnippetSummary]): List[SnippetSummary] = {

      xs.groupBy(_.snippetId.base64UUID)
        .toList
        .flatMap {
          case (_, snippets) =>
            List(
              snippets.sortBy(_.snippetId.user.map(_.update).getOrElse(0)).last
            )
        }
        .sortBy(_.time)
        .reverse
    }

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
        sortSnippets(summaries)
          .map(
            summary =>
              div(cls := "group", key := summary.snippetId.base64UUID)(
                renderSnippet(scope.backend, props)(summary)
            )
          )
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
          delta.backend.loadProfile0()

        loadProfile.when_(viewChangedToCodeSnippet)
      }
      .componentWillMount(_.backend.loadProfile0())
      .configure(Reusability.shouldComponentUpdate)
      .build
}
