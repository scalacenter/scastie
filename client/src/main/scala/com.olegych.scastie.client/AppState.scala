package com.olegych.scastie
package client

import api._

import upickle.default.{ReadWriter, macroRW => upickleMacroRW}

import org.scalajs.dom.{EventSource, WebSocket}
import org.scalajs.dom.raw.HTMLElement

object AppState {
  def default = AppState(
    view = View.Editor,
    isRunning = false,
    eventSource = None,
    websocket = None,
    modalState = ModalState.default,
    isDarkTheme = false,
    consoleState = ConsoleState.default,
    inputsHasChanged = false,
    snippetId = None,
    isSnippetSaved = false,
    loadSnippet = true,
    loadScalaJsScript = false,
    isScalaJsScriptLoaded = false,
    snippetIdIsScalaJS = false,
    isReRunningScalaJs = false,
    user = None,
    attachedDoms = Map(),
    inputs = Inputs.default,
    outputs = Outputs.default,
    windowHasResized = false,
    dimensions = Dimensions.default
  )

  implicit val dontSerializeAttachedDoms: ReadWriter[AttachedDoms] =
    dontSerializeMap[String, HTMLElement]

  implicit val dontSerializeWebSocket: ReadWriter[Option[WebSocket]] =
    dontSerializeOption[WebSocket]

  implicit val dontSerializeEventSource: ReadWriter[Option[EventSource]] =
    dontSerializeOption[EventSource]

  implicit val pkl: ReadWriter[AppState] =
    upickleMacroRW[AppState]
}

case class AppState(
    view: View,
    isRunning: Boolean,
    eventSource: Option[EventSource],
    websocket: Option[WebSocket],
    modalState: ModalState,
    isDarkTheme: Boolean,
    consoleState: ConsoleState,
    inputsHasChanged: Boolean,
    snippetId: Option[SnippetId],
    isSnippetSaved: Boolean,
    loadSnippet: Boolean,
    loadScalaJsScript: Boolean,
    isScalaJsScriptLoaded: Boolean,
    snippetIdIsScalaJS: Boolean,
    isReRunningScalaJs: Boolean,
    user: Option[User],
    attachedDoms: AttachedDoms,
    inputs: Inputs,
    outputs: Outputs,
    windowHasResized: Boolean,
    dimensions: Dimensions
) {
  def copyAndSave(view: View = view,
                  isRunning: Boolean = isRunning,
                  eventSource: Option[EventSource] = eventSource,
                  websocket: Option[WebSocket] = websocket,
                  modalState: ModalState = modalState,
                  isDarkTheme: Boolean = isDarkTheme,
                  consoleState: ConsoleState = consoleState,
                  inputsHasChanged: Boolean = inputsHasChanged,
                  snippetId: Option[SnippetId] = snippetId,
                  snippetIdIsScalaJS: Boolean = snippetIdIsScalaJS,
                  user: Option[User] = user,
                  inputs: Inputs = inputs,
                  outputs: Outputs = outputs,
                  windowHasResized: Boolean = windowHasResized,
                  dimensions: Dimensions = dimensions): AppState = {

    val snippetId0 =
      if (inputsHasChanged) None
      else snippetId

    val isScalaJsScriptLoaded0 =
      if (inputsHasChanged) false
      else isScalaJsScriptLoaded

    val isSnippetSaved0 =
      if (inputsHasChanged) false
      else isSnippetSaved

    val state0 =
      copy(
        view,
        isRunning,
        eventSource,
        websocket,
        modalState,
        isDarkTheme,
        consoleState,
        inputsHasChanged,
        snippetId0,
        isSnippetSaved0,
        loadSnippet,
        loadScalaJsScript,
        isScalaJsScriptLoaded0,
        snippetIdIsScalaJS,
        isReRunningScalaJs,
        user,
        attachedDoms,
        inputs.copy(
          showInUserProfile = false,
          forked = None
        ),
        outputs,
        windowHasResized,
        dimensions
      )

    LocalStorage.save(state0)

    state0
  }

  def isClearable: Boolean =
    outputs.isClearable

  def run(snippetId: SnippetId): AppState = {
    clearOutputs
      .resetScalajs
      .setRunning(true)
      .logSystem("Connecting.")
      .copyAndSave(inputsHasChanged = false)
      .setSnippetId(snippetId)
  }

  def setRunning(isRunning: Boolean): AppState = {
    val console = !isRunning && !consoleState.consoleHasUserOutput
    copyAndSave(isRunning = isRunning, consoleState = consoleState.copy(consoleIsOpen = !console))
  }

  def setIsReRunningScalaJs(value: Boolean): AppState =
    copy(isReRunningScalaJs = value)

  def setSnippetSaved: AppState =
    copy(isSnippetSaved = true)

  def toggleForcedDesktop(value: Boolean): AppState =
    copyAndSave(dimensions = dimensions.copy(forcedDesktop = value))

  def toggleTheme: AppState =
    copyAndSave(isDarkTheme = !isDarkTheme)

  def toggleConsole: AppState =
    copyAndSave(consoleState = consoleState.copy(consoleIsOpen = !consoleState.consoleIsOpen))

  def setWindowHasResized: AppState =
    copyAndSave(windowHasResized = !windowHasResized)

  def toggleWorksheetMode: AppState =
    copyAndSave(
      inputs = inputs.copy(worksheetMode = !inputs.worksheetMode),
      inputsHasChanged = true
    )

  def toggleWelcome: AppState =
    copyAndSave(modalState = modalState.copy(isWelcomeModalClosed = !modalState.isWelcomeModalClosed))

  def toggleHelp: AppState =
    copyAndSave(modalState = modalState.copy(isHelpModalClosed = !modalState.isHelpModalClosed))

  def toggleReset: AppState =
    copyAndSave(modalState = modalState.copy(isResetModalClosed = !modalState.isResetModalClosed))

  def toggleWelcomeHelp: AppState =
    copyAndSave(modalState = modalState.copy(
      isWelcomeModalClosed = !modalState.isWelcomeModalClosed,
      isHelpModalClosed = !modalState.isHelpModalClosed))

  def toggleShare(snippetId: Option[SnippetId]): AppState =
    copyAndSave(modalState = modalState.copy(isShareModalClosed = !modalState.isShareModalClosed),
      snippetId = snippetId)

  def openConsole: AppState =
    copyAndSave(consoleState = consoleState.copy(consoleIsOpen = true))

  def setUserOutput: AppState =
    copyAndSave(consoleState = consoleState.copy(consoleHasUserOutput = true))

  def setLoadSnippet(value: Boolean): AppState =
    copy(loadSnippet = value)

  def setUser(user: Option[User]): AppState =
    copyAndSave(user = user)

  def setDimensionsHaveChanged(value: Boolean): AppState =
    copyAndSave(
      dimensions = dimensions.copy(dimensionsHaveChanged = value))

  def setTopBarHeight(height: Int): AppState =
    copyAndSave(
      dimensions = dimensions.copy(topBarHeight = height))

  def setEditorTopBarHeight(height: Int): AppState =
    copyAndSave(
      dimensions = dimensions.copy(editorTopBarHeight = height))

  def setSideBarWidth(width: Int): AppState =
    copyAndSave(
      dimensions = dimensions.copy(sideBarWidth = width))

  def setSideBarMinHeight(height: Int): AppState =
    copyAndSave(
      dimensions = dimensions.copy(sideBarMinHeight = height))

  def setConsoleBarHeight(height: Int): AppState =
    copyAndSave(
      dimensions = dimensions.copy(consoleBarHeight = height))

  def setConsoleHeight(height: Int): AppState =
    copyAndSave(
      dimensions = dimensions.copy(consoleHeight = height))

  def setCode(code: String): AppState =
    copyAndSave(
      inputs = inputs.copy(code = code),
      inputsHasChanged = true
    )

  def setInputs(inputs: Inputs): AppState =
    copyAndSave(
      inputs = inputs
    )

  def setSbtConfigExtra(config: String): AppState =
    copyAndSave(
      inputs = inputs.copy(sbtConfigExtra = config),
      inputsHasChanged = true
    )

  def setCleanInputs: AppState =
    copyAndSave(inputsHasChanged = false)

  def setView(newView: View): AppState =
    copyAndSave(view = newView)

  def setTarget(target: ScalaTarget): AppState =
    copyAndSave(
      inputs = inputs.copy(target = target),
      inputsHasChanged = true
    )

  def addScalaDependency(scalaDependency: ScalaDependency,
                         project: Project): AppState =
    copyAndSave(
      inputs = inputs.addScalaDependency(scalaDependency, project),
      inputsHasChanged = true
    )

  def removeScalaDependency(scalaDependency: ScalaDependency): AppState =
    copyAndSave(
      inputs = inputs.removeScalaDependency(scalaDependency),
      inputsHasChanged = true
    )

  def updateDependencyVersion(scalaDependency: ScalaDependency,
                              version: String): AppState = {
    val newScalaDependency = scalaDependency.copy(version = version)
    copyAndSave(
      inputs = inputs.copy(
        libraries = (inputs.libraries - scalaDependency) + newScalaDependency
      ),
      inputsHasChanged = true
    )
  }

  def scalaJsScriptLoaded: AppState =
    copy(isScalaJsScriptLoaded = true)

  def resetScalajs: AppState =
    copy(
      attachedDoms = Map(),
      isScalaJsScriptLoaded = false,
      loadScalaJsScript = true
    )

  def clearOutputs: AppState =
    copyAndSave(
      outputs = Outputs.default,
      consoleState = consoleState.copy(consoleIsOpen = false, consoleHasUserOutput = false)
    )

  def setRuntimeError(runtimeError: Option[RuntimeError]): AppState =
    if (runtimeError.isEmpty) this
    else copyAndSave(outputs = outputs.copy(runtimeError = runtimeError))

  def logOutput(line: Option[String],
                wrap: String => ConsoleOutput): AppState = {
    line match {
      case Some(l) =>
        copyAndSave(
          outputs = outputs.copy(
            consoleOutputs = outputs.consoleOutputs ++ Vector(wrap(l))
          )
        )
      case _ => this
    }
  }

  def logSystem(line: String): AppState = {
    copyAndSave(
      outputs = outputs.copy(
        consoleOutputs = outputs.consoleOutputs ++ Vector(
          ConsoleOutput.ScastieOutput(line)
        )
      )
    )
  }

  def addProgress(progress: SnippetProgress): AppState = {
    val state =
      addOutputs(progress.compilationInfos, progress.instrumentations)
        .logOutput(progress.userOutput, ConsoleOutput.UserOutput(_))
        .logOutput(progress.sbtOutput, ConsoleOutput.SbtOutput(_))
        .setForcedProgramMode(progress.forcedProgramMode)
        .setRunning(!progress.done)
        .setLoadScalaJsScript(loadScalaJsScript | progress.done)
        .setRuntimeError(progress.runtimeError)

    if (progress.userOutput.isDefined) state.setUserOutput
    else state
  }

  def setProgresses(progresses: List[SnippetProgress]): AppState = {
    progresses.foldLeft(this) {
      case (state, progress) => state.addProgress(progress)
    }
  }

  def setSnippetId(snippetId: SnippetId): AppState = {
    copyAndSave(
      snippetId = Some(snippetId),
      snippetIdIsScalaJS = inputs.target.targetType == ScalaTargetType.JS
    )
  }

  private def info(message: String) = Problem(api.Info, None, message)

  def setForcedProgramMode(forcedProgramMode: Boolean): AppState = {
    if (!forcedProgramMode) this
    else {
      copyAndSave(
        outputs = outputs.copy(
          compilationInfos = outputs.compilationInfos +
            info(
              "You don't need a main method (or extends App) in Worksheet Mode"
            )
        )
      )
    }
  }

  def setLoadScalaJsScript(value: Boolean): AppState = {
    copy(loadScalaJsScript = value)
  }

  def addOutputs(compilationInfos: List[api.Problem],
                 instrumentations: List[api.Instrumentation]): AppState = {

    def topDef(problem: api.Problem): Boolean = {
      problem.severity == api.Error &&
      problem.message == "expected class or object definition"
    }

    val useWorksheetModeTip =
      if (compilationInfos.exists(ci => topDef(ci)))
        Set(
          info(
            """|It seems you're writing code without an enclosing class/object.
               |Switch to Worksheet mode if you want to use scastie more like a REPL.""".stripMargin
          )
        )
      else Set()

    copyAndSave(
      outputs = outputs.copy(
        compilationInfos = outputs.compilationInfos ++ compilationInfos.toSet ++ useWorksheetModeTip,
        instrumentations = outputs.instrumentations ++ instrumentations.toSet
      )
    )
  }
}
