package org.scastie
package client
package components

import org.scastie.api.{SnippetId, User, ScalaTarget}

import org.scastie.client.i18n.I18n

import japgolly.scalajs.react._, vdom.all._, extra.router._, extra._
import org.scastie.api.ScalaTargetType

final case class EditorTopBar(clear: Reusable[Callback],
                              closeNewSnippetModal: Reusable[Callback],
                              closeEmbeddedModal: Reusable[Callback],
                              openEmbeddedModal: Reusable[Callback],
                              formatCode: Reusable[Callback],
                              newSnippet: Reusable[Callback],
                              openNewSnippetModal: Reusable[Callback],
                              save: Reusable[Callback],
                              toggleWorksheetMode: Reusable[Callback],
                              router: Option[RouterCtl[Page]],
                              inputsHasChanged: Boolean,
                              isDarkTheme: Boolean,
                              isNewSnippetModalClosed: Boolean,
                              isEmbeddedModalClosed: Boolean,
                              isRunning: Boolean,
                              isStatusOk: Boolean,
                              snippetId: Option[SnippetId],
                              user: Option[User],
                              view: StateSnapshot[View],
                              isWorksheetMode: Boolean,
                              metalsStatus: MetalsStatus,
                              toggleMetalsStatus: Reusable[Callback],
                              scalaTarget: ScalaTarget,
                              language: String) {
  @inline def render: VdomElement = EditorTopBar.component(this)
}

object EditorTopBar {

  implicit val reusability: Reusability[EditorTopBar] =
    Reusability.derive[EditorTopBar]

  private def render(props: EditorTopBar): VdomElement = {
    def isDisabled = (cls := "disabled").when(props.view.value != View.Editor)

    val runButton = RunButton(
      isRunning = props.isRunning,
      isStatusOk = props.isStatusOk,
      save = props.save,
      setView = Reusable.fn(view => props.view.setState(view)),
      embedded = false,
    ).render

    val newButton = NewButton(
      isDarkTheme = props.isDarkTheme,
      isNewSnippetModalClosed = props.isNewSnippetModalClosed,
      openNewSnippetModal = props.openNewSnippetModal,
      closeNewSnippetModal = props.closeNewSnippetModal,
      newSnippet = props.newSnippet,
      language = props.language
    ).render

    val formatButton = FormatButton(
      inputsHasChanged = props.inputsHasChanged,
      formatCode = props.formatCode,
      isStatusOk = props.isStatusOk,
      language = props.language
    ).render

    val clearButton = ClearButton(
      clear = props.clear,
      language = props.language
    ).render

    val worksheetButton = WorksheetButton(
      props.scalaTarget.targetType != ScalaTargetType.ScalaCli,
      props.isWorksheetMode,
      props.toggleWorksheetMode,
      props.view.value,
      props.language
    ).render

    val metalsButton = MetalsStatusIndicator(
      props.metalsStatus,
      props.toggleMetalsStatus,
      props.view.value,
    ).render

    val downloadButton =
      props.snippetId match {
        case Some(sid) =>
          DownloadButton(snippetId = sid, language = props.language).render
        case _ =>
          EmptyVdom
      }

    val embeddedModalButton =
      (props.snippetId, props.router) match {
        case (Some(sid), Some(router)) =>
          val url = router.urlFor(Page.fromSnippetId(sid)).value

          val content =
            s"""<script src="$url.js"></script>""".stripMargin

          val embeddedModal =
            CopyModal(
              isDarkTheme = props.isDarkTheme,
              title = I18n.t("editor.embed_title"),
              subtitle = I18n.t("editor.embed_subtitle"),
              modalId = "embed-modal",
              content = content,
              isClosed = props.isEmbeddedModalClosed,
              close = props.closeEmbeddedModal
            ).render

          li(title := I18n.t("editor.embed"), role := "button", cls := "btn", onClick --> props.openEmbeddedModal)(
            i(cls := "fa fa-code"),
            span(I18n.t("editor.embed")),
            embeddedModal
          )
        case _ => EmptyVdom
      }

    nav(cls := "editor-topbar", isDisabled)(
      ul(cls := "editor-buttons")(
        runButton,
        newButton,
        formatButton,
        clearButton,
        worksheetButton,
        downloadButton,
        embeddedModalButton,
        metalsButton
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[EditorTopBar]("EditorTopBar")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
