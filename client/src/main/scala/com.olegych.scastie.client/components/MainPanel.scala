package com.olegych.scastie.client.components

import com.olegych.scastie.client.{ScastieBackend, ScastieState, View}
import com.olegych.scastie.client.components.editor.Editor

import japgolly.scalajs.react._, vdom.all._, extra._

final case class MainPanel(state: ScastieState,
                           backend: ScastieBackend,
                           props: Scastie) {

  @inline def render: VdomElement = MainPanel.component(this)
}

object MainPanel {
  implicit val reusability: Reusability[MainPanel] =
    Reusability.caseClass[MainPanel]

  def render(in: MainPanel): VdomElement = {
    import in._

    def show(view: View) =
      if (view == state.view) display.block
      else display.none

    val isStatusOk = state.status.isSbtOk

    val embeddedMenu =
      EmbeddedMenu(
        isRunning = state.isRunning,
        isStatusOk = isStatusOk,
        inputs = state.inputs,
        inputsHasChanged = state.inputsHasChanged,
        embeddedSnippetId = props.embedded.flatMap(_.snippetId),
        serverUrl = props.serverUrl,
        run = backend.run,
        save = backend.saveBlocking,
        setView = backend.setViewReused
      ).render.when(props.isEmbedded)

    val consoleCssForEditor =
      (cls := "console-open").when(state.consoleState.consoleIsOpen)

    val codeSnippets =
      (props.router, state.user) match {
        case (Some(router), Some(user)) if state.view == View.CodeSnippets =>
          div(cls := "snippets-container inner-container")(
            CodeSnippets(
              view = state.view,
              user = user,
              router = router,
              isShareModalClosed = state.modalState.isShareModalClosed,
              closeShareModal = backend.closeShareModal,
              openShareModal = backend.openShareModal,
              loadProfile = backend.loadProfile,
              deleteSnippet = backend.deleteSnippet
            ).render
          )
        case _ => EmptyVdom
      }

    val editor =
      Editor(
        isDarkTheme = state.isDarkTheme,
        isPresentationMode = state.isPresentationMode,
        isEmbedded = props.isEmbedded,
        showLineNumbers = state.showLineNumbers,
        code = state.inputs.code,
        attachedDoms = state.attachedDoms,
        instrumentations = state.outputs.instrumentations,
        compilationInfos = state.outputs.compilationInfos,
        runtimeError = state.outputs.runtimeError,
        completions = state.completions,
        typeAtInfo = state.typeAtInfo,
        run = backend.run,
        saveOrUpdate = backend.saveOrUpdate,
        clear = backend.clear,
        openNewSnippetModal = backend.openNewSnippetModal,
        toggleHelp = backend.toggleHelpModal,
        toggleConsole = backend.toggleConsole,
        toggleWorksheetMode = backend.toggleWorksheetMode,
        toggleTheme = backend.toggleTheme,
        toggleLineNumbers = backend.toggleLineNumbers,
        togglePresentationMode = backend.togglePresentationMode,
        formatCode = backend.formatCode,
        codeChange = backend.codeChange,
        completeCodeAt = backend.completeCodeAt,
        requestTypeAt = backend.typeAt,
        clearCompletions = backend.clearCompletions
      ).render

    val console =
      Console(
        isOpen = state.consoleState.consoleIsOpen,
        isRunning = state.isRunning,
        content = state.outputs.console,
        close = backend.closeConsole,
        open = backend.openConsole
      ).render

    val buildSettings =
      BuildSettings(
        librariesFrom = state.inputs.librariesFrom,
        isDarkTheme = state.isDarkTheme,
        isBuildDefault = state.isBuildDefault,
        isResetModalClosed = state.modalState.isResetModalClosed,
        scalaTarget = state.inputs.target,
        sbtConfigExtra = state.inputs.sbtConfigExtra,
        setTarget = backend.setTarget,
        closeResetModal = backend.closeResetModal,
        resetBuild = backend.resetBuild,
        openResetModal = backend.openResetModal,
        sbtConfigChange = backend.sbtConfigChange,
        removeScalaDependency = backend.removeScalaDependency,
        updateDependencyVersion = backend.updateDependencyVersion,
        addScalaDependency = backend.addScalaDependency
      ).render

    val mobileBar =
      MobileBar(
        isRunning = state.isRunning,
        isStatusOk = isStatusOk,
        run = backend.run,
        setView = backend.setViewReused,
        forceDesktop = backend.forceDesktop
      ).render

    val topBar =
      TopBar(
        backend.viewSnapshot(state.view),
        state.user
      ).render.unless(props.isEmbedded || state.isPresentationMode)

    val editorTopBar =
      EditorTopBar(
        amend = backend.amend,
        clear = backend.clear,
        closeNewSnippetModal = backend.closeNewSnippetModal,
        fork = backend.fork,
        formatCode = backend.formatCode,
        newSnippet = backend.newSnippet,
        openNewSnippetModal = backend.openNewSnippetModal,
        run = backend.run,
        save = backend.save,
        toggleWorksheetMode = backend.toggleWorksheetMode,
        update = backend.update,
        inputsHasChanged = state.inputsHasChanged,
        isNewSnippetModalClosed = state.modalState.isNewSnippetModalClosed,
        isRunning = state.isRunning,
        isStatusOk = isStatusOk,
        isSnippetSaved = state.isSnippetSaved,
        snippetId = state.snippetId,
        user = state.user,
        view = backend.viewSnapshot(state.view),
        worksheetMode = state.inputs.worksheetMode,
        targetType = state.inputs.target.targetType
      ).render.unless(props.isEmbedded || state.isPresentationMode)

    val statusView =
      props.router match {
        case Some(router) =>
          Status(
            state = state.status,
            router = router,
            isAdmin = state.user.exists(_.isAdmin),
            inputs = state.inputs
          ).render
        case _ => EmptyVdom
      }

    val presentationModeClass =
      (cls := "presentation-mode").when(state.isPresentationMode)

    div(
      cls := "main-panel",
      presentationModeClass,
      topBar,
      editorTopBar,
      div(
        cls := "content",
        div(cls := "editor-container inner-container", show(View.Editor))(
          div(cls := "code", consoleCssForEditor)(
            editor,
            embeddedMenu
          ),
          console
        ),
        div(cls := "settings-container inner-container",
            show(View.BuildSettings))(
          buildSettings
        ),
        div(cls := "status-container inner-container", show(View.Status))(
          statusView
        ),
        codeSnippets,
        mobileBar
      )
    )

  }

  private val component =
    ScalaComponent
      .builder[MainPanel]("MainPanel")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
