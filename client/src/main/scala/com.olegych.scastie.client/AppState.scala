package com.olegych.scastie
package client

import api._

import upickle.default.{ReadWriter, macroRW => upickleMacroRW}

import org.scalajs.dom.{EventSource, WebSocket}
import org.scalajs.dom.raw.HTMLElement

object AppState {
  def default = AppState(
    view = View.Editor,
    running = false,
    eventSource = None,
    websocket = None,
    isHelpModalClosed = true,
    isWelcomeModalClosed = false,
    isShareModalClosed = true,
    isDarkTheme = false,
    consoleState = ConsoleState.default,
    inputsHasChanged = false,
    snippetId = None,
    loadSnippet = true,
    loadScalaJsScript = false,
    isScalaJsScriptLoaded = false,
    snippetIdIsScalaJS = false,
    codeSnippetCopied = false,
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
  implicit val pkl: ReadWriter[AppState] = upickleMacroRW[AppState]
}

case class AppState(
    view: View,
    running: Boolean,
    eventSource: Option[EventSource],
    websocket: Option[WebSocket],
    isWelcomeModalClosed: Boolean,
    isHelpModalClosed: Boolean,
    isShareModalClosed: Boolean,
    isDarkTheme: Boolean,
    consoleState: ConsoleState,
    inputsHasChanged: Boolean,
    snippetId: Option[SnippetId],
    loadSnippet: Boolean,
    loadScalaJsScript: Boolean,
    isScalaJsScriptLoaded: Boolean,
    snippetIdIsScalaJS: Boolean,
    codeSnippetCopied: Boolean,
    user: Option[User],
    attachedDoms: AttachedDoms,
    inputs: Inputs,
    outputs: Outputs,
    windowHasResized: Boolean,
    dimensions: Dimensions
) {
  def copyAndSave(view: View = view,
                  running: Boolean = running,
                  eventSource: Option[EventSource] = eventSource,
                  websocket: Option[WebSocket] = websocket,
                  isWelcomeModalClosed: Boolean = isWelcomeModalClosed,
                  isHelpModalClosed: Boolean = isHelpModalClosed,
                  isShareModalClosed: Boolean = isShareModalClosed,
                  isDarkTheme: Boolean = isDarkTheme,
                  consoleState: ConsoleState = consoleState,
                  inputsHasChanged: Boolean = inputsHasChanged,
                  snippetId: Option[SnippetId] = snippetId,
                  snippetIdIsScalaJS: Boolean = snippetIdIsScalaJS,
                  codeSnippetCopied: Boolean = codeSnippetCopied,
                  user: Option[User] = user,
                  attachedDoms: AttachedDoms = attachedDoms,
                  inputs: Inputs = inputs,
                  outputs: Outputs = outputs,
                  windowHasResized: Boolean = windowHasResized,
                  dimensions: Dimensions = dimensions): AppState = {

    val snippetId0 =
      if (inputsHasChanged) None
      else snippetId

    val state0 =
      copy(
        view,
        running,
        eventSource,
        websocket,
        isWelcomeModalClosed,
        isHelpModalClosed,
        isShareModalClosed,
        isDarkTheme,
        consoleState,
        inputsHasChanged,
        snippetId0,
        loadSnippet,
        loadScalaJsScript,
        isScalaJsScriptLoaded,
        snippetIdIsScalaJS,
        codeSnippetCopied,
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
    resetOutputs
      .setRunning(true)
      .logSystem("Connecting.")
      .copyAndSave(inputsHasChanged = false)
      .setSnippetId(snippetId)
  }

  def setRunning(running: Boolean): AppState = {
    val console = !running && !consoleState.consoleHasUserOutput
    copyAndSave(running = running, consoleState = consoleState.copy(consoleIsOpen = !console))
  }

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
    copyAndSave(isWelcomeModalClosed = !isWelcomeModalClosed)

  def toggleHelp: AppState =
    copyAndSave(isHelpModalClosed = !isHelpModalClosed)

  def toggleWelcomeHelp: AppState =
    copyAndSave(
      isWelcomeModalClosed = !isWelcomeModalClosed,
      isHelpModalClosed = !isHelpModalClosed)

  def toggleShare(snippetId: Option[SnippetId]): AppState =
    copyAndSave(isShareModalClosed = !isShareModalClosed,
      snippetId = snippetId)

  def toggleSnippetCopied(): AppState =
    copyAndSave(codeSnippetCopied = !codeSnippetCopied)

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

  def setTopBarHeight(height: Double): AppState =
    copyAndSave(
      dimensions = dimensions.copy(topBarHeight = height))

  def setEditorTopBarHeight(height: Double): AppState =
    copyAndSave(
      dimensions = dimensions.copy(editorTopBarHeight = height))

  def setSideBarWidth(width: Double): AppState =
    copyAndSave(
      dimensions = dimensions.copy(sideBarWidth = width))

  def setSideBarMinHeight(height: Double): AppState =
    copyAndSave(
      dimensions = dimensions.copy(sideBarMinHeight = height))

  def setConsoleBarHeight(height: Double): AppState =
    copyAndSave(
      dimensions = dimensions.copy(consoleBarHeight = height))

  def setConsoleHeight(height: Double): AppState =
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

  def resetOutputs: AppState =
    copyAndSave(outputs = Outputs.default,
      consoleState = consoleState.copy(consoleIsOpen = false, consoleHasUserOutput = false),
      attachedDoms = Map()).copy(isScalaJsScriptLoaded = false)

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
          ))
      case _ => this
    }
  }

  def logSystem(line: String): AppState = {
    copyAndSave(
      outputs = outputs.copy(
        consoleOutputs = outputs.consoleOutputs ++ Vector(
            ConsoleOutput.ScastieOutput(line))
      ))
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

    if (!progress.userOutput.isEmpty) state.setUserOutput
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
      copyAndSave(outputs = outputs.copy(
        compilationInfos = outputs.compilationInfos +
            info(
              "You don't need a main method (or extends App) in Worksheet Mode")
      ))
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
          info("""|It seems you're writing code without an enclosing class/object. 
                  |Switch to Worksheet mode if you want to use scastie more like a REPL.""".stripMargin))
      else Set()

    copyAndSave(outputs = outputs.copy(
      compilationInfos = outputs.compilationInfos ++ compilationInfos.toSet ++ useWorksheetModeTip,
      instrumentations = outputs.instrumentations ++ instrumentations.toSet
    ))
  }
}
