package com.olegych.scastie
package client
package components

import api.{SnippetId, User, ScalaTargetType}

import japgolly.scalajs.react._, vdom.all._, extra._

final case class EditorTopBar(amend: SnippetId ~=> Callback,
                              clear: Reusable[Callback],
                              closeNewSnippetModal: Reusable[Callback],
                              fork: SnippetId ~=> Callback,
                              formatCode: Reusable[Callback],
                              newSnippet: Reusable[Callback],
                              openNewSnippetModal: Reusable[Callback],
                              run: Reusable[Callback],
                              save: Reusable[Callback],
                              toggleWorksheetMode: Reusable[Callback],
                              update: SnippetId ~=> Callback,
                              inputsHasChanged: Boolean,
                              isNewSnippetModalClosed: Boolean,
                              isRunning: Boolean,
                              isStatusOk: Boolean,
                              isSnippetSaved: Boolean,
                              snippetId: Option[SnippetId],
                              user: Option[User],
                              view: StateSnapshot[View],
                              worksheetMode: Boolean,
                              targetType: ScalaTargetType) {
  @inline def render: VdomElement = EditorTopBar.component(this)
}

object EditorTopBar {

  implicit val reusability: Reusability[EditorTopBar] =
    Reusability.caseClass[EditorTopBar]

  private def render(props: EditorTopBar): VdomElement = {
    def isDisabled = (cls := "disabled").when(props.view.value != View.Editor)

    val runButton = RunButton(
      isRunning = props.isRunning,
      isStatusOk = props.isStatusOk,
      run = props.run,
      setView = props.view.setState
    ).render

    val newButton = NewButton(
      isNewSnippetModalClosed = props.isNewSnippetModalClosed,
      openNewSnippetModal = props.openNewSnippetModal,
      closeNewSnippetModal = props.closeNewSnippetModal,
      newSnippet = props.newSnippet
    ).render

    val formatButton = FormatButton(
      inputsHasChanged = props.inputsHasChanged,
      formatCode = props.formatCode,
      isStatusOk = props.isStatusOk
    ).render

    val clearButton = ClearButton(
      clear = props.clear
    ).render

    val worksheetButton = WorksheetButton(
      props.worksheetMode,
      props.toggleWorksheetMode,
      props.view.value
    ).render

    val saveButton = SaveButton(
      isSnippetSaved = props.isSnippetSaved,
      inputsHasChanged = props.inputsHasChanged,
      user = props.user,
      snippetId = props.snippetId,
      amend = props.amend,
      update = props.update,
      save = props.save,
      fork = props.fork
    ).render

    val downloadButton =
      props.snippetId match {
        case Some(sid) if props.isSnippetSaved =>
          DownloadButton(snippetId = sid).render

        case _ =>
          EmptyVdom
      }

    nav(cls := "editor-topbar", isDisabled)(
      ul(cls := "editor-buttons")(
        runButton,
        newButton,
        formatButton,
        clearButton,
        worksheetButton.when(props.targetType != ScalaTargetType.Dotty),
        downloadButton,
        saveButton
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
