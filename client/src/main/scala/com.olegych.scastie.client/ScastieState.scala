package com.olegych.scastie.client

import com.olegych.scastie.api._
import com.olegych.scastie.proto._

import scalajs.js.debugger

import org.scalajs.dom.{EventSource, WebSocket}
import org.scalajs.dom.raw.HTMLElement

object ScastieState {
  def default = ScastieState(
    eventSource = None,
    websocket = None,
    statusEventSource = None,
    attachedDoms = AttachedDoms(Map()),
    status = StatusState.default,
    completions = List(),
    typeAtInfo = None,
    persistedState = ScastiePersistedState(
      view = View.Editor,
      isRunning = false,
      modalState = ModalStateHelper.default,
      isDarkTheme = false,
      isDesktopForced = false,
      isPresentationMode = false,
      showLineNumbers = false,
      consoleState = ConsoleStateHelper.default,
      inputsHasChanged = false,
      snippetState = SnippetState(
        snippetId = None,
        isSnippetSaved = false,
        loadSnippet = true,
        loadScalaJsScript = false,
        isScalaJsScriptLoaded = false,
        snippetIdIsScalaJS = false,
        isReRunningScalaJs = false
      ),
      user = None,
      inputs = InputsHelper.default,
      outputs = Outputs.default,
    )
  )
}

case class ScastieState(
    eventSource: Option[EventSource],
    statusEventSource: Option[EventSource],
    websocket: Option[WebSocket],
    attachedDoms: AttachedDoms,
    status: StatusState,
    completions: List[EnsimeResponse.Completion],
    typeAtInfo: Option[EnsimeResponse.TypeAtPoint],
    persistedState: ScastiePersistedState
) {
  import persistedState._

  // def view
  // def isRunning
  // def modalState
  // def isDarkTheme
  // def isDesktopForced
  // def isPresentationMode
  // def showLineNumbers
  // def consoleState
  // def inputsHasChanged
  // def snippetState
  // def user
  // def inputs
  // def outputs


  def snippetId: Option[SnippetId] = snippetState.snippetId
  def isSnippetSaved: Boolean = snippetState.isSnippetSaved
  def loadSnippet: Boolean = snippetState.loadSnippet
  def loadScalaJsScript: Boolean = snippetState.loadScalaJsScript
  def isScalaJsScriptLoaded: Boolean = snippetState.isScalaJsScriptLoaded
  def snippetIdIsScalaJS: Boolean = snippetState.snippetIdIsScalaJS
  def isReRunningScalaJs: Boolean = snippetState.isReRunningScalaJs

  def copyAndSave(view: View = view,
                  isRunning: Boolean = isRunning,
                  eventSource: Option[EventSource] = eventSource,
                  websocket: Option[WebSocket] = websocket,
                  modalState: ModalState = modalState,
                  isDarkTheme: Boolean = isDarkTheme,
                  isPresentationMode: Boolean = isPresentationMode,
                  isDesktopForced: Boolean = isDesktopForced,
                  showLineNumbers: Boolean = showLineNumbers,
                  consoleState: ConsoleState = consoleState,
                  inputsHasChanged: Boolean = inputsHasChanged,
                  snippetId: Option[SnippetId] = snippetId,
                  snippetIdIsScalaJS: Boolean = snippetIdIsScalaJS,
                  user: Option[User] = user,
                  inputs: Inputs = inputs,
                  outputs: Outputs = outputs,
                  status: StatusState = status): ScastieState = {

    val isScalaJsScriptLoaded0 =
      if (inputsHasChanged) false
      else isScalaJsScriptLoaded

    val state0 =
      copy(
        view,
        isRunning,
        eventSource,
        statusEventSource,
        websocket,
        modalState,
        isDarkTheme,
        isDesktopForced,
        isPresentationMode,
        showLineNumbers,
        consoleState,
        inputsHasChanged,
        SnippetState(
          snippetId,
          isSnippetSaved,
          loadSnippet,
          loadScalaJsScript,
          isScalaJsScriptLoaded0,
          snippetIdIsScalaJS,
          isReRunningScalaJs
        ),
        user,
        attachedDoms,
        inputs.copy(
          showInUserProfile = false,
          forked = None
        ),
        outputs,
        status,
        completions,
        typeAtInfo
      )

    LocalStorage.save(state0)

    state0
  }

  def isBuildDefault: Boolean = inputs.isDefault

  def isClearable: Boolean =
    outputs.isClearable

  def run(snippetId: SnippetId): ScastieState = {
    clearOutputs.resetScalajs
      .setRunning(true)
      .logSystem("Sending task to the server.")
      .copyAndSave(inputsHasChanged = false)
      .setSnippetId(snippetId)
  }

  def setRunning(isRunning: Boolean): ScastieState = {
    val openConsole =
      isRunning || consoleState.consoleHasUserOutput || outputs.sbtError

    copyAndSave(isRunning = isRunning).setConsoleAuto(openConsole)
  }

  def setIsReRunningScalaJs(value: Boolean): ScastieState =
    copy(snippetState = snippetState.copy(isReRunningScalaJs = value))

  def setSnippetSaved(value: Boolean): ScastieState = {
    copy(snippetState = snippetState.copy(isSnippetSaved = value),
         inputsHasChanged = false)
  }

  def toggleTheme: ScastieState =
    copyAndSave(isDarkTheme = !isDarkTheme)

  def setTheme(dark: Boolean): ScastieState =
    copyAndSave(isDarkTheme = dark)

  def toggleLineNumbers: ScastieState =
    copyAndSave(showLineNumbers = !showLineNumbers)

  def togglePresentationMode: ScastieState =
    copyAndSave(isPresentationMode = !isPresentationMode)

  def toggleWorksheetMode: ScastieState =
    copyAndSave(
      inputs = inputs.copy(worksheetMode = !inputs.worksheetMode),
      inputsHasChanged = true
    )

  def openWelcomeModal: ScastieState =
    copyAndSave(modalState = modalState.copy(isWelcomeModalClosed = false))

  def closeWelcomeModal: ScastieState =
    copyAndSave(modalState = modalState.copy(isWelcomeModalClosed = true))

  def openHelpModal: ScastieState =
    copyAndSave(modalState = modalState.copy(isHelpModalClosed = false))

  def closeHelpModal: ScastieState =
    copyAndSave(modalState = modalState.copy(isHelpModalClosed = true))

  def openResetModal: ScastieState =
    copyAndSave(modalState = modalState.copy(isResetModalClosed = false))

  def closeResetModal: ScastieState =
    copyAndSave(modalState = modalState.copy(isResetModalClosed = true))

  def openNewSnippetModal: ScastieState =
    copyAndSave(modalState = modalState.copy(isNewSnippetModalClosed = false))

  def closeNewSnippetModal: ScastieState =
    copyAndSave(modalState = modalState.copy(isNewSnippetModalClosed = true))

  def openShareModal(snippetId: Option[SnippetId]): ScastieState =
    copyAndSave(modalState = modalState.copy(shareModalSnippetId = snippetId))

  def closeShareModal: ScastieState =
    copyAndSave(modalState = modalState.copy(shareModalSnippetId = None))

  def forceDesktop: ScastieState = copyAndSave(isDesktopForced = true)

  def openConsole: ScastieState = {
    copyAndSave(
      consoleState = consoleState.copy(
        consoleIsOpen = true,
        userOpenedConsole = true
      )
    )
  }

  def closeConsole: ScastieState = {
    copyAndSave(
      consoleState = consoleState.copy(
        consoleIsOpen = false,
        userOpenedConsole = false
      )
    )
  }

  def setConsoleAuto(isOpen: Boolean): ScastieState = {
    if (!isOpen && consoleState.userOpenedConsole)
      this
    else
      copyAndSave(
        consoleState = consoleState.copy(
          consoleIsOpen = isOpen
        )
      )
  }

  def toggleConsole: ScastieState = {
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

  def setUserOutput: ScastieState =
    copyAndSave(consoleState = consoleState.copy(consoleHasUserOutput = true))

  def setLoadSnippet(value: Boolean): ScastieState =
    copy(snippetState = snippetState.copy(loadSnippet = value))

  def setUser(user: Option[User]): ScastieState =
    copyAndSave(user = user)

  def setCode(code: String): ScastieState =
    copyAndSave(
      inputs = inputs.copy(code = code),
      inputsHasChanged = true
    )

  def setInputs(inputs: Inputs): ScastieState =
    copyAndSave(
      inputs = inputs
    )

  def setCompletions(completions: List[Completion]): ScastieState =
    copy(completions = completions)

  def setTypeAtInto(typeAtInfoAt: Option[TypeInfoAt]): ScastieState =
    copy(typeAtInfo = typeAtInfoAt)

  def setSbtConfigExtra(config: String): ScastieState =
    copyAndSave(
      inputs = inputs.copy(sbtConfigExtra = config),
      inputsHasChanged = true
    )

  def setChangedInputs: ScastieState =
    copyAndSave(inputsHasChanged = true)

  def setCleanInputs: ScastieState =
    copyAndSave(inputsHasChanged = false)

  def setView(newView: View): ScastieState =
    copyAndSave(view = newView)

  def setTarget(target: ScalaTarget): ScastieState =
    copyAndSave(
      inputs = inputs.copy(target = target),
      inputsHasChanged = true
    )

  def addScalaDependency(scalaDependency: ScalaDependency,
                         project: Project): ScastieState =
    copyAndSave(
      inputs = inputs.addScalaDependency(scalaDependency, project),
      inputsHasChanged = true
    )

  def removeScalaDependency(scalaDependency: ScalaDependency): ScastieState =
    copyAndSave(
      inputs = inputs.removeScalaDependency(scalaDependency),
      inputsHasChanged = true
    )

  def updateDependencyVersion(scalaDependency: ScalaDependency,
                              version: String): ScastieState = {
    val newScalaDependency = scalaDependency.copy(version = version)
    copyAndSave(
      inputs = inputs.copy(
        libraries = (inputs.libraries - scalaDependency) + newScalaDependency
      ),
      inputsHasChanged = true
    )
  }

  def scalaJsScriptLoaded: ScastieState =
    copy(snippetState = snippetState.copy(isScalaJsScriptLoaded = true))

  def resetScalajs: ScastieState =
    copy(
      attachedDoms = AttachedDoms(Map()),
      snippetState = snippetState.copy(
        isScalaJsScriptLoaded = false,
        loadScalaJsScript = true
      )
    )

  def clearOutputs: ScastieState =
    copyAndSave(
      outputs = Outputs.default,
      consoleState = consoleState.copy(
        consoleIsOpen = false,
        consoleHasUserOutput = false
      )
    )

  def closeModals: ScastieState =
    copyAndSave(modalState = ModalStateHelper.allClosed)

  def setRuntimeError(runtimeError: Option[RuntimeError]): ScastieState =
    if (runtimeError.isEmpty) this
    else copyAndSave(outputs = outputs.copy(runtimeError = runtimeError))

  def setSbtError(err: Boolean): ScastieState =
    copyAndSave(outputs = outputs.copy(sbtError = err))

  def logOutput(line: Option[String],
                wrap: String => ConsoleOutput): ScastieState = {
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

  def logSystem(line: String): ScastieState = {
    copyAndSave(
      outputs = outputs.copy(
        consoleOutputs = outputs.consoleOutputs ++ Vector(
          ConsoleOutput.ScastieOutput(line)
        )
      )
    )
  }

  def addProgress(progress: SnippetProgress): ScastieState = {
    val state =
      addOutputs(progress.compilationInfos, progress.instrumentations)
        .logOutput(progress.userOutput, ConsoleOutput.UserOutput)
        .logOutput(progress.sbtOutput, ConsoleOutput.SbtOutput)
        .setForcedProgramMode(progress.forcedProgramMode)
        .setLoadScalaJsScript(loadScalaJsScript | progress.done)
        .setRuntimeError(progress.runtimeError)
        .setSbtError(progress.sbtError)
        .setRunning(!progress.done)

    if (progress.userOutput.isDefined) state.setUserOutput
    else state
  }

  def addStatus(status: StatusProgress): ScastieState = {
    status match {
      case StatusKeepAlive     => this
      case StatusInfo(runners) => copy(status = StatusState(Some(runners)))
    }
  }

  def removeStatus: ScastieState = {
    copy(status = StatusState(None))
  }

  def setProgresses(progresses: List[SnippetProgress]): ScastieState = {
    progresses.foldLeft(this) {
      case (state, progress) => state.addProgress(progress)
    }
  }

  def setSnippetId(snippetId: SnippetId): ScastieState = {
    copyAndSave(
      snippetId = Some(snippetId),
      snippetIdIsScalaJS = inputs.target.targetType == ScalaTargetType.JS
    )
  }

  def clearSnippetId: ScastieState = {
    copyAndSave(
      snippetId = None,
      snippetIdIsScalaJS = false
    ).setSnippetSaved(false)
  }

  private def info(message: String) = Problem(Info, None, message)

  def setForcedProgramMode(forcedProgramMode: Boolean): ScastieState = {
    if (!forcedProgramMode) this
    else {
      copyAndSave(
        outputs = outputs.copy(
          compilationInfos = outputs.compilationInfos +
            info(
              "You don't need a main method (or extends Scastie) in Worksheet Mode"
            )
        )
      )
    }
  }

  def setLoadScalaJsScript(value: Boolean): ScastieState = {
    copy(snippetState = snippetState.copy(loadScalaJsScript = value))
  }

  def addOutputs(compilationInfos: List[Problem],
                 instrumentations: List[Instrumentation]): ScastieState = {

    def topDef(problem: Problem): Boolean = {
      problem.severity == Error &&
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
