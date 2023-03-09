package com.olegych.scastie.client.components

import com.olegych.scastie.client.ScastieBackend
import com.olegych.scastie.client.ScastieState
import com.olegych.scastie.client.View
import com.olegych.scastie.client.components.editor.CodeEditor
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

final case class MainPanel(state: ScastieState, backend: ScastieBackend, props: Scastie) {

  @inline def render: VdomElement = MainPanel.component(this)
}

object MainPanel {
  implicit val reusability: Reusability[MainPanel] =
    Reusability.derive[MainPanel]

  def render(in: MainPanel): VdomElement = {
    import in._

    def visible(view: View) = view == state.view
    def show(view: View) =
      if (visible(view)) display.block
      else display.none

    val isStatusOk = state.status.isSbtOk

    val embeddedMenu =
      EmbeddedOverlay(
        inputsHasChanged = state.inputsHasChanged,
        embeddedSnippetId = props.embeddedSnippetId,
        serverUrl = props.serverUrl,
        save = backend.saveBlocking,
      ).render.when(props.isEmbedded)

    val consoleCssForEditor =
      (cls := "console-open").when(state.consoleState.consoleIsOpen)

    val codeSnippets =
      (props.router, state.user) match {
        case (Some(router), Some(user)) if state.view == View.CodeSnippets =>
          div(cls := "snippets-container inner-container")(
            CodeSnippets(
              isDarkTheme = state.isDarkTheme,
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
      CodeEditor(
        visible = visible(View.Editor),
        isDarkTheme = state.isDarkTheme,
        isPresentationMode = state.isPresentationMode,
        isWorksheetMode = state.inputs.isWorksheetMode,
        isEmbedded = props.isEmbedded,
        showLineNumbers = state.showLineNumbers,
        value = state.inputs.code,
        attachedDoms = state.attachedDoms,
        instrumentations = state.outputs.instrumentations,
        compilationInfos = state.outputs.compilationInfos,
        runtimeError = state.outputs.runtimeError,
        saveOrUpdate = backend.saveOrUpdate,
        clear = backend.clear,
        openNewSnippetModal = backend.openNewSnippetModal,
        toggleHelp = backend.toggleHelpModal,
        toggleConsole = backend.toggleConsole,
        toggleLineNumbers = backend.toggleLineNumbers,
        togglePresentationMode = backend.togglePresentationMode,
        formatCode = backend.formatCode,
        codeChange = backend.codeChange,
        target = state.inputs.target,
        metalsStatus = state.metalsStatus,
        setMetalsStatus = backend.setMetalsStatus,
        dependencies = state.inputs.libraries
      ).render

    val console =
      Console(
        isOpen = state.consoleState.consoleIsOpen,
        isRunning = state.isRunning,
        isEmbedded = props.isEmbedded,
        consoleOutputs = state.outputs.consoleOutputs,
        run = backend.run,
        setView = backend.setViewReused,
        close = backend.closeConsole,
        open = backend.openConsole
      ).render

    val buildSettings =
      BuildSettings(
        visible = visible(View.BuildSettings),
        librariesFrom = state.inputs.librariesFrom,
        isDarkTheme = state.isDarkTheme,
        isBuildDefault = state.isBuildDefault,
        isResetModalClosed = state.modalState.isResetModalClosed,
        scalaTarget = state.inputs.target,
        sbtConfigExtra = state.inputs.sbtConfigExtra,
        sbtConfig = state.inputs.sbtConfigGenerated,
        sbtPluginsConfig = state.inputs.sbtPluginsConfigGenerated,
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
        isDarkTheme = state.isDarkTheme,
        save = backend.saveOrUpdate,
        setView = backend.setViewReused,
        clear = backend.clear,
        isNewSnippetModalClosed = state.modalState.isNewSnippetModalClosed,
        openNewSnippetModal = backend.openNewSnippetModal,
        closeNewSnippetModal = backend.closeNewSnippetModal,
        newSnippet = backend.newSnippet,
        forceDesktop = backend.forceDesktop
      ).render

    val topBar =
      TopBar(
        backend.viewSnapshot(state.view),
        state.user,
        backend.openLoginModal
      ).render.unless(props.isEmbedded || state.isPresentationMode)

    val editorTopBar =
      EditorTopBar(
        clear = backend.clear,
        closeNewSnippetModal = backend.closeNewSnippetModal,
        closeEmbeddedModal = backend.closeEmbeddedModal,
        openEmbeddedModal = backend.openEmbeddedModal,
        formatCode = backend.formatCode,
        newSnippet = backend.newSnippet,
        openNewSnippetModal = backend.openNewSnippetModal,
        save = backend.saveOrUpdate,
        toggleWorksheetMode = backend.toggleWorksheetMode,
        router = props.router,
        inputsHasChanged = state.inputsHasChanged,
        isDarkTheme = state.isDarkTheme,
        isNewSnippetModalClosed = state.modalState.isNewSnippetModalClosed,
        isEmbeddedModalClosed = state.modalState.isEmbeddedClosed,
        isRunning = state.isRunning,
        isStatusOk = isStatusOk,
        snippetId = state.snippetId,
        user = state.user,
        view = backend.viewSnapshot(state.view),
        isWorksheetMode = state.inputs.isWorksheetMode,
        metalsStatus = state.metalsStatus,
        toggleMetalsStatus = backend.toggleMetalsStatus,
        scalaTarget = state.inputs.target
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
        div(cls := "settings-container inner-container", show(View.BuildSettings))(
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
