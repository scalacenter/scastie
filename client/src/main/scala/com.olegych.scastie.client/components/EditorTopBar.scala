package com.olegych.scastie
package client
package components

import api.{SnippetId, User, ScalaTargetType}

import japgolly.scalajs.react._, vdom.all._

final case class EditorTopBar(amend: SnippetId => Callback,
                              clear: Callback,
                              closeNewSnippetModal: Callback,
                              fork: SnippetId => Callback,
                              formatCode: Callback,
                              newSnippet: Callback,
                              openNewSnippetModal: Callback,
                              run: Callback,
                              save: Callback,
                              setView: View => Callback,
                              toggleWorksheetMode: Callback,
                              update: SnippetId => Callback,
                              inputsHasChanged: Boolean,
                              isNewSnippetModalClosed: Boolean,
                              isRunning: Boolean,
                              isStatusOk: Boolean,
                              isSnippetSaved: Boolean,
                              snippetId: Option[SnippetId],
                              user: Option[User],
                              view: View,
                              worksheetMode: Boolean,
                              targetType: ScalaTargetType) {
  @inline def render: VdomElement = EditorTopBar.component(this)
}

object EditorTopBar {

  private def render(props: EditorTopBar): VdomElement = {
    def isDisabled = (cls := "disabled").when(props.view != View.Editor)

    val runButton = RunButton(
      isRunning = props.isRunning,
      isStatusOk = props.isStatusOk,
      run = props.run,
      setView = props.setView
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
      props.view
    ).render

    val saveButton = SaveButton(
      isSnippetSaved = props.isSnippetSaved,
      user = props.user,
      snippetId = props.snippetId,
      amend = props.amend,
      update = props.update,
      save = props.save,
      fork = props.fork
    ).render

    val downloadButton =
      props.snippetId match {
        case Some(sid) => DownloadButton(snippetId = sid).render
        case None => EmptyVdom
      }

    nav(cls := "editor-topbar", isDisabled)(
      ul(cls := "editor-buttons")(
        runButton,
        newButton,
        formatButton,
        clearButton,
        worksheetButton.when(props.targetType != ScalaTargetType.Dotty),
        saveButton,
        downloadButton
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[EditorTopBar]("EditorTopBar")
      .render_P(render)
      .build
}
