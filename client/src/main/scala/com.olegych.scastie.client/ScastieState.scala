package com.olegych.scastie.client

import play.api.libs.json._

import com.olegych.scastie.api._

object SnippetState {
  implicit val formatSnippetState: OFormat[SnippetState] =
    Json.format[SnippetState]
}


case class SnippetState(
    snippetId: Option[SnippetId],
    isSnippetSaved: Boolean,
    loadSnippet: Boolean,
    loadScalaJsScript: Boolean,
    isScalaJsScriptLoaded: Boolean,
    snippetIdIsScalaJS: Boolean,
    isReRunningScalaJs: Boolean
)

object ScastieState {
  def default(isEmbedded: Boolean): ScastieState = {
    ScastieState(
      view = View.Editor,
      isRunning = false,
      statusStream = None,
      progressStream = None,
      modalState =
        if (isEmbedded) ModalState.allClosed
        else ModalState.default,
      isDarkTheme = false,
      isDesktopForced = false,
      isPresentationMode = false,
      showLineNumbers = false,
      consoleState = ConsoleState.default,
      inputsHasChanged = false,
      ensimeConfigurationLoading = false,
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
      attachedDoms = AttachedDoms(Map()),
      inputs = Inputs.default,
      outputs = Outputs.default,
      status = StatusState.empty,
      completions = List(),
      typeAtInfo = None,
      isEmbedded = isEmbedded
    )
  }

  implicit val dontSerializeCompletions: Format[List[Completion]] =
    dontSerializeList[Completion]

  implicit val dontSerializeAttachedDoms: Format[AttachedDoms] =
    dontSerialize[AttachedDoms](AttachedDoms(Map()))

  implicit val dontSerializeStatusState: Format[StatusState] =
    dontSerialize[StatusState](StatusState.empty)

  type TypeInfoAtHint = Option[TypeInfoAt]

  implicit val dontSerializeTypeInfoAt: Format[TypeInfoAtHint] =
    dontSerializeOption[TypeInfoAt]

  type StatusProgressHint = Option[EventStream[StatusProgress]]

  implicit val dontSerializeStatusStream: Format[StatusProgressHint] =
    dontSerializeOption[EventStream[StatusProgress]]

  type SnippetProgressHint = Option[EventStream[SnippetProgress]]

  implicit val dontSerializeProgressStream: Format[SnippetProgressHint] =
    dontSerializeOption[EventStream[SnippetProgress]]

  implicit val formatScastieState: OFormat[ScastieState] =
    Json.format[ScastieState]
}

case class ScastieState(
    view: View,
    isRunning: Boolean,
    statusStream: ScastieState.StatusProgressHint,
    progressStream: ScastieState.SnippetProgressHint,
    modalState: ModalState,
    isDarkTheme: Boolean,
    isDesktopForced: Boolean,
    isPresentationMode: Boolean,
    showLineNumbers: Boolean,
    consoleState: ConsoleState,
    inputsHasChanged: Boolean,
    ensimeConfigurationLoading: Boolean,
    snippetState: SnippetState,
    user: Option[User],
    attachedDoms: AttachedDoms,
    inputs: Inputs,
    outputs: Outputs,
    status: StatusState,
    completions: List[Completion],
    typeAtInfo: ScastieState.TypeInfoAtHint,
    isEmbedded: Boolean = false
) {

  def snippetId: Option[SnippetId] = snippetState.snippetId
  def isSnippetSaved: Boolean = snippetState.isSnippetSaved
  def loadSnippet: Boolean = snippetState.loadSnippet
  def loadScalaJsScript: Boolean = snippetState.loadScalaJsScript
  def isScalaJsScriptLoaded: Boolean = snippetState.isScalaJsScriptLoaded
  def snippetIdIsScalaJS: Boolean = snippetState.snippetIdIsScalaJS
  def isReRunningScalaJs: Boolean = snippetState.isReRunningScalaJs

  def copyAndSave(
      view: View = view,
      isRunning: Boolean = isRunning,
      statusStream: Option[EventStream[StatusProgress]] = statusStream,
      progressStream: Option[EventStream[SnippetProgress]] = progressStream,
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
      status: StatusState = status
  ): ScastieState = {

    val isScalaJsScriptLoaded0 =
      if (inputsHasChanged) false
      else isScalaJsScriptLoaded

    val state0 =
      copy(
        view,
        isRunning,
        statusStream,
        progressStream,
        modalState,
        isDarkTheme,
        isDesktopForced,
        isPresentationMode,
        showLineNumbers,
        consoleState,
        inputsHasChanged,
        ensimeConfigurationLoading,
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
        typeAtInfo,
        isEmbedded
      )

    if (!isEmbedded) {
      LocalStorage.save(state0)
    }

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

  def toggleHelpModal: ScastieState =
    copyAndSave(
      modalState =
        modalState.copy(isHelpModalClosed = !modalState.isHelpModalClosed)
    )

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

  def setCode(code: String): ScastieState = {
    if (inputs.code != code) {
      copyAndSave(
        inputs = inputs.copy(code = code),
        inputsHasChanged = true
      )
    } else {
      this
    }
  }

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
    copyAndSave(modalState = ModalState.allClosed)

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

  def addStatus(statusUpdate: StatusProgress): ScastieState = {
    statusUpdate match {
      case StatusProgress.KeepAlive => {
        this
      }
      case StatusProgress.Sbt(sbtRunners) => {
        copy(status = status.copy(sbtRunners = Some(sbtRunners)))
      }
      case StatusProgress.Ensime(ensimeRunners) => {
        val updatedStatus =
          status.copy(ensimeRunners = Some(ensimeRunners))

        val ensimeConfigurationLoading0 =
          ensimeConfigurationLoading &&
            updatedStatus.ensimeReady(inputs)

        copy(
          status = updatedStatus,
          ensimeConfigurationLoading = ensimeConfigurationLoading0
        )

      }
    }
  }

  def removeStatus: ScastieState = {
    copy(status = StatusState.empty)
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
