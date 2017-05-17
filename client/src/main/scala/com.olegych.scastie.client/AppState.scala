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
    isDesktopForced = false,
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
    outputs = Outputs.default
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
    isDesktopForced: Boolean,
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
    outputs: Outputs
) {
  def copyAndSave(view: View = view,
                  isRunning: Boolean = isRunning,
                  eventSource: Option[EventSource] = eventSource,
                  websocket: Option[WebSocket] = websocket,
                  modalState: ModalState = modalState,
                  isDarkTheme: Boolean = isDarkTheme,
                  isDesktopForced: Boolean = isDesktopForced,
                  consoleState: ConsoleState = consoleState,
                  inputsHasChanged: Boolean = inputsHasChanged,
                  snippetId: Option[SnippetId] = snippetId,
                  snippetIdIsScalaJS: Boolean = snippetIdIsScalaJS,
                  user: Option[User] = user,
                  inputs: Inputs = inputs,
                  outputs: Outputs = outputs): AppState = {

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
        isDesktopForced,
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
        outputs
      )

    LocalStorage.save(state0)

    state0
  }

  def isClearable: Boolean =
    outputs.isClearable

  def run(snippetId: SnippetId): AppState = {
    clearOutputs.resetScalajs
      .setRunning(true)
      .logSystem("Connecting.")
      .copyAndSave(inputsHasChanged = false)
      .setSnippetId(snippetId)
  }

  def setRunning(isRunning: Boolean): AppState = {
    val openConsole =
      isRunning || consoleState.consoleHasUserOutput || outputs.sbtError

    copyAndSave(isRunning = isRunning).setConsoleAuto(openConsole)
  }

  def setIsReRunningScalaJs(value: Boolean): AppState =
    copy(isReRunningScalaJs = value)

  def setSnippetSaved(value: Boolean): AppState =
    copy(isSnippetSaved = value, inputsHasChanged = false)

  def toggleTheme: AppState =
    copyAndSave(isDarkTheme = !isDarkTheme)

  def toggleWorksheetMode: AppState =
    copyAndSave(
      inputs = inputs.copy(worksheetMode = !inputs.worksheetMode),
      inputsHasChanged = true
    )

  def openWelcomeModal: AppState =
    copyAndSave(modalState = modalState.copy(isWelcomeModalClosed = false))

  def closeWelcomeModal: AppState =
    copyAndSave(modalState = modalState.copy(isWelcomeModalClosed = true))

  def openHelpModal: AppState =
    copyAndSave(modalState = modalState.copy(isHelpModalClosed = false))

  def closeHelpModal: AppState =
    copyAndSave(modalState = modalState.copy(isHelpModalClosed = true))

  def openResetModal: AppState =
    copyAndSave(modalState = modalState.copy(isResetModalClosed = false))

  def closeResetModal: AppState =
    copyAndSave(modalState = modalState.copy(isResetModalClosed = true))

  def openNewSnippetModal: AppState =
    copyAndSave(modalState = modalState.copy(isNewSnippetModalClosed = false))

  def closeNewSnippetModal: AppState =
    copyAndSave(modalState = modalState.copy(isNewSnippetModalClosed = true))

  def openShareModal(snippetId: Option[SnippetId]): AppState =
    copyAndSave(modalState = modalState.copy(shareModalSnippetId = snippetId))

  def closeShareModal: AppState =
    copyAndSave(modalState = modalState.copy(shareModalSnippetId = None))

  def forceDesktop: AppState = copyAndSave(isDesktopForced = true)

  def openConsole: AppState = {
    copyAndSave(
      consoleState = consoleState.copy(
        consoleIsOpen = true,
        userOpenedConsole = true
      )
    )
  }

  def closeConsole: AppState = {
    copyAndSave(
      consoleState = consoleState.copy(
        consoleIsOpen = false,
        userOpenedConsole = false
      )
    )
  }

  def setConsoleAuto(isOpen: Boolean): AppState = {
    if (!isOpen && consoleState.userOpenedConsole)
      this
    else
      copyAndSave(
        consoleState = consoleState.copy(
          consoleIsOpen = isOpen
        )
      )
  }

  def toggleConsole: AppState = {
    copyAndSave(
      consoleState =
        if (consoleState.consoleIsOpen)
          consoleState.copy(
            consoleIsOpen = false,
            userOpenedConsole = false
          )
        else
          consoleState.copy(
            consoleIsOpen = true,
            userOpenedConsole = true
          )
    )
  }

  def setUserOutput: AppState =
    copyAndSave(consoleState = consoleState.copy(consoleHasUserOutput = true))

  def setLoadSnippet(value: Boolean): AppState =
    copy(loadSnippet = value)

  def setUser(user: Option[User]): AppState =
    copyAndSave(user = user)

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

  def setChangedInputs: AppState =
    copyAndSave(inputsHasChanged = true)

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
      consoleState = consoleState.copy(
        consoleIsOpen = false,
        consoleHasUserOutput = false
      )
    )

  def closeModals: AppState = copyAndSave(modalState = ModalState.allClosed)

  def setRuntimeError(runtimeError: Option[RuntimeError]): AppState =
    if (runtimeError.isEmpty) this
    else copyAndSave(outputs = outputs.copy(runtimeError = runtimeError))

  def setSbtError(err: Boolean): AppState =
    copyAndSave(outputs = outputs.copy(sbtError = err))

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
        .setLoadScalaJsScript(loadScalaJsScript | progress.done)
        .setRuntimeError(progress.runtimeError)
        .setSbtError(progress.sbtError)
        .setRunning(!progress.done)

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

  def clearSnippetId: AppState = {
    copyAndSave(
      snippetId = None,
      snippetIdIsScalaJS = false
    ).setSnippetSaved(false)
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
