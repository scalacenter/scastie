package com.olegych.scastie
package client

import api._
import japgolly.scalajs.react._
import autowire._
import org.scalajs.dom

import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom._
import upickle.default.{read => uread}

import scala.util.{Failure, Success}

class AppBackend(scope: BackendScope[AppProps, AppState]) {
  Global.subsribe(scope)

  def goHome(e: ReactEventI): Callback = {
    scope.props.flatMap(
      props => props.router.map(_.set(Home)).getOrElse(Callback(()))
    ) >>
      scope.modState(_.setView(View.Editor))
  }

  def resetBuildAndClose(e: ReactEventI): Callback =
    resetBuild >> toggleReset()

  def resetBuild: Callback =
    scope.modState(
      state => state.setInputs(Inputs.default.copy(code = state.inputs.code))
    )

  def codeChange(newCode: String) =
    scope.modState(_.setCode(newCode))

  def sbtConfigChange(newConfig: String) =
    scope.modState(_.setSbtConfigExtra(newConfig))

  private def snippetUri(snippetId: SnippetId,
                         connectionMethod: String): String = {
    val snippetPart =
      snippetId.user match {
        case Some(SnippetUserPart(login, update)) => {
          val updatePart = update.map(_.toString).getOrElse("")
          s"$login/${snippetId.base64UUID}/$updatePart"
        }
        case None => s"${snippetId.base64UUID}"
      }

    s"/$connectionMethod/$snippetPart"
  }

  private def connectEventSource(snippetId: SnippetId) =
    CallbackTo[EventSource] {
      val direct = scope.accessDirect

      val eventSource = new EventSource(snippetUri(snippetId, "progress-sse"))

      def onopen(e: Event): Unit = {
        direct.modState(_.logSystem("Connected."))
      }
      def onmessage(e: MessageEvent): Unit = {
        val progress = uread[SnippetProgress](e.data.toString)

        direct.modState(_.addProgress(progress))

        if (progress.done) {
          direct.modState(
            _.copy(eventSource = None, isRunning = false)
          )
          eventSource.close()
        }
      }
      def onerror(e: Event): Unit = {
        if (e.eventPhase == EventSource.CLOSED) {
          eventSource.close()
        } else {
          direct.modState(_.logSystem(s"Error: ${e.toString}"))
        }
      }

      eventSource.onopen = onopen _
      eventSource.onmessage = onmessage _
      eventSource.onerror = onerror _
      eventSource
    }

  private def connectWebSocket(snippetId: SnippetId) = CallbackTo[WebSocket] {
    val direct = scope.accessDirect

    def onopen(e: Event): Unit = direct.modState(_.logSystem("Connected."))
    def onmessage(e: MessageEvent): Unit = {
      val progress = uread[SnippetProgress](e.data.toString)
      direct.modState(_.addProgress(progress))
    }
    def onerror(e: ErrorEvent): Unit =
      direct.modState(_.logSystem(s"Error: ${e.message}"))
    def onclose(e: CloseEvent): Unit =
      direct.modState(
        _.copy(websocket = None, isRunning = false)
          .logSystem(s"Closed: ${e.reason}")
      )

    val protocol = if (window.location.protocol == "https:") "wss" else "ws"
    val connectionPart = snippetUri(snippetId, "progress-websocket")
    val uri = s"$protocol://${window.location.host}${connectionPart}"
    val socket = new WebSocket(uri)

    socket.onopen = onopen _
    socket.onclose = onclose _
    socket.onmessage = onmessage _
    socket.onerror = onerror _
    socket
  }

  def newSnippet(e: ReactEventI): Callback = {

    val snippetId = SnippetId("", None)

    val setData = scope.modState(_.clearOutputs
      .setInputs(Inputs.default.copy(code = ""))
      .setSnippetSaved(false)
      .setSnippetId(snippetId)
      .setChangedInputs)

    val setPage = scope.props.flatMap(
      props =>
        props.router
          .map(_.set(Page.fromSnippetId(snippetId)))
          .getOrElse(Callback(()))
    )

    setData >> setPage

  }

  def clear(e: ReactEventI): Callback = clear()
  def clear(): Callback = scope.modState(_.clearOutputs)

  def clearCode(): Callback = scope.modState(_.setCode(""))

  def toggleForcedDesktop(value: Boolean)(e: ReactEventI): Callback =
    scope.modState(_.toggleForcedDesktop(value).setDimensionsHaveChanged(true))

  def toggleMobile(): Callback =
    scope.modState(_.toggleForcedDesktop(value = false))
  def toggleMobile(e: ReactEventI): Callback =
    toggleMobile() >> setView(View.Editor) >> setDimensionsHaveChanged(true)

  def setView(newView: View): Callback =
    scope.modState(_.setView(newView))

  def setView2(newView: View)(e: ReactEventI): Callback =
    setView(newView)

  def setTarget2(target: ScalaTarget)(e: ReactEventI): Callback =
    setTarget(target)

  def setTarget(target: ScalaTarget): Callback =
    scope.modState(_.setTarget(target))

  def addScalaDependency(scalaDependency: ScalaDependency,
                         project: Project): Callback =
    scope.modState(_.addScalaDependency(scalaDependency, project))

  def removeScalaDependency(scalaDependency: ScalaDependency): Callback =
    scope.modState(_.removeScalaDependency(scalaDependency))

  def updateDependencyVersion(scalaDependency: ScalaDependency,
                              version: String): Callback =
    scope.modState(_.updateDependencyVersion(scalaDependency, version))

  def toggleTheme(e: ReactEventI): Callback = toggleTheme()
  def toggleTheme(): Callback = scope.modState(_.toggleTheme)

  def toggleConsole(): Callback = scope.modState(_.toggleConsole)
  def toggleConsole(e: ReactEventI): Callback =
    toggleConsole() >> setDimensionsHaveChanged(true)

  def setWindowHasResized(): Callback = scope.modState(_.setWindowHasResized)

  def toggleWelcome(e: ReactEventI): Callback = scope.modState(_.toggleWelcome)

  def toggleHelp(e: ReactEventI): Callback = scope.modState(_.toggleHelp)

  def toggleReset(e: ReactEventI): Callback = toggleReset()
  def toggleReset(): Callback = scope.modState(_.toggleReset)

  def toggleWelcomeHelp(e: ReactEventI): Callback =
    scope.modState(_.toggleWelcomeHelp)

  def toggleShare(snippetId: Option[SnippetId])(e: ReactEventI): Callback =
    scope.modState(_.toggleShare(snippetId))

  def toggleWorksheetMode(): Callback = scope.modState(_.toggleWorksheetMode)
  def toggleWorksheetMode(e: ReactEventI): Callback = toggleWorksheetMode()

  def setDimensionsHaveChanged(value: Boolean): Callback =
    scope.modState(_.setDimensionsHaveChanged(value))

  def setTopBarHeight(): Callback =
    scope.modState(_.setTopBarHeight(getElementHeight("topbar")))

  def setEditorTopBarHeight(): Callback =
    scope.modState(_.setEditorTopBarHeight(getElementHeight("editor-topbar")))

  def setSideBarWidth(): Callback =
    scope.modState(_.setSideBarWidth(getElementWidth("sidebar")))

  def setSideBarMinHeight(): Callback =
    scope.modState(
      _.setSideBarMinHeight(
        getElementHeight("topbar") + getElementHeight("actions-top") + getElementHeight(
          "actions-bottom"
        )
      )
    )

  def setConsoleBarHeight(): Callback =
    scope.modState(_.setConsoleBarHeight(getElementHeight("switcher-show")))

  def setConsoleHeight(): Callback =
    scope.modState(
      _.setConsoleHeight(
        getElementHeight("console")
      )
    )

  def setMobileBarHeight(): Callback =
    scope.modState(
      _.setMobileBarHeight(
        getElementHeight("editor-mobile")
      )
    )

  def setDimensions(): Callback =
    setTopBarHeight() >>
      setEditorTopBarHeight() >>
      setSideBarWidth() >>
      setSideBarMinHeight >>
      setConsoleBarHeight() >>
      setConsoleHeight() >>
      setMobileBarHeight() >>
      setDimensionsHaveChanged(false)

  def getElementWidth(id: String): Int =
    Option(dom.document.getElementById(id)).map(_.clientWidth).getOrElse(0)

  def getElementHeight(id: String): Int =
    Option(dom.document.getElementById(id)).map(_.clientHeight).getOrElse(0)

  def run(e: ReactEventI): Callback = run()
  def run(): Callback = {
    scope.state.flatMap(
      state =>
        if (!state.isScalaJsScriptLoaded || state.inputsHasChanged) {
          Callback.future(
            ApiClient[AutowireApi]
              .run(state.inputs)
              .call()
              .map(
                snippetId =>
                  connectEventSource(snippetId).attemptTry.flatMap {
                    case Success(eventSource) => {
                      scope.modState(
                        _.run(snippetId)
                          .copy(eventSource = Some(eventSource))
                      )
                    }
                    case Failure(errorEventSource) =>
                      connectWebSocket(snippetId).attemptTry.flatMap {
                        case Success(websocket) => {
                          scope.modState(
                            _.run(snippetId)
                              .copy(websocket = Some(websocket))
                          )
                        }
                        case Failure(errorWebSocket) =>
                          scope.modState(
                            _.clearOutputs
                              .logSystem(errorEventSource.toString)
                              .logSystem(errorWebSocket.toString)
                              .setRunning(false)
                          )
                      }
                }
              )
          )
        } else {
          scope.modState(_.setIsReRunningScalaJs(true))
      }
    )
  }

  def save(e: ReactEventI): Callback = save()
  def save(): Callback = {
    scope.state.flatMap(
      state =>
        if (!state.isSnippetSaved) {
            Callback.future(
              ApiClient[AutowireApi]
                .save(state.inputs)
                .call()
                .map(
                  snippetId =>
                    scope.modState(
                      _.setLoadSnippet(false).setCleanInputs.setSnippetSaved(true)
                    ) >>
                    scope.props.flatMap(
                      props =>
                        props.router
                          .map(_.set(Page.fromSnippetId(snippetId)))
                          .getOrElse(Callback(()))
                  )
                )
            )
        } else Callback(())
    )
  }

  def saveOrUpdate(): Callback = {
    scope.props.flatMap(
      props =>
        props.snippetId match {
          case Some(snippetId) => update2(snippetId)
          case None => save()
      }
    )
  }

  def amend(snippetId: SnippetId)(e: ReactEventI): Callback = {
    scope.state.flatMap(
      state =>
        if (!state.isSnippetSaved) {
          Callback.future(
            ApiClient[AutowireApi]
              .amend(snippetId, state.inputs)
              .call()
              .map(
                success =>
                  if (success) scope.modState(_.setSnippetSaved(true))
                  else Callback(window.alert("Failed to amend"))
              )
          )
        } else Callback(())
    )
  }

  def fork(snippetId: SnippetId)(e: ReactEventI): Callback = {
    scope.state.flatMap(
      state =>
        if (!state.isSnippetSaved) {
          Callback.future(
            ApiClient[AutowireApi]
              .fork(snippetId, state.inputs)
              .call()
              .map {
                case Some(sId) =>
                  val page = Page.fromSnippetId(sId)
                  scope.modState(_.setSnippetSaved(true)) >>
                  scope.modState(
                    _.setLoadSnippet(false).clearOutputs
                  ) >>
                  scope.props.flatMap(
                    _.router.map(_.set(page)).getOrElse(Callback(()))
                  )
                case None => Callback(window.alert("Failed to fork"))
              }
          )
        } else Callback(())
    )
  }

  def update(snippetId: SnippetId)(e: ReactEventI): Callback =
    update2(snippetId)
  def update2(snippetId: SnippetId): Callback = {
    scope.state.flatMap(
      state =>
        if (!state.isSnippetSaved) {
          Callback.future(
            ApiClient[AutowireApi]
              .update(snippetId, state.inputs)
              .call()
              .map {
                case Some(sId) =>
                  val page = Page.fromSnippetId(sId)
                  scope.modState(_.setSnippetSaved(true)) >>
                  scope.modState(
                    _.setLoadSnippet(false).clearOutputs
                  ) >>
                  scope.props.flatMap(
                    _.router.map(_.set(page)).getOrElse(Callback(()))
                  )
                case None => Callback(window.alert("Failed to update"))
              }
          )
        } else Callback(())
    )
  }

  def loadSnippet(snippetId: SnippetId): Callback = {
    scope.state.flatMap(
      state =>
        if (state.loadSnippet) {
          Callback.future(
            ApiClient[AutowireApi]
              .fetch(snippetId)
              .call()
              .map {
                case Some(FetchResult(inputs, progresses)) =>
                  loadStateFromLocalStorage >>
                    clear() >>
                    scope.modState(
                      _.setInputs(inputs)
                        .setProgresses(progresses)
                        .setCleanInputs
                    )
                case _ =>
                  scope.modState(_.setCode(s"//snippet not found"))
              }
          ) >> setView(View.Editor) >> scope.modState(_.clearOutputs.closeModals)
        } else {
          scope.modState(_.setLoadSnippet(true))
      }
    ) >> scope.modState(_.setSnippetId(snippetId))
  }

  def loadStateFromLocalStorage =
    LocalStorage.load
      .map(
        state =>
          scope.modState(
            _ =>
              state
                .setRunning(false)
                .resetScalajs
        )
      )
      .getOrElse(Callback(()))

  def start(props: AppProps): Callback = {

    def onresize(e: UIEvent): Unit =
      (setWindowHasResized() >> setDimensionsHaveChanged(true)).runNow

    dom.window.onresize = onresize _

    def loadUser(): Callback = {
      Callback.future(
        ApiClient[AutowireApi]
          .fetchUser()
          .call()
          .map(result => scope.modState(_.setUser(result)))
      )
    }

    val initialState =
      props.embedded match {
        case None => {
          props.snippetId match {
            case Some(snippetId) => loadSnippet(snippetId)
            case None => loadStateFromLocalStorage
          }
        }
        case Some(embededOptions) => {
          embededOptions match {
            case EmbededOptions(Some(snippetId), _) => loadSnippet(snippetId)
            case EmbededOptions(_, Some(inputs)) =>
              scope.modState(_.setInputs(inputs))
            case _ => Callback(())
          }
        }
      }

    initialState >> loadUser()
  }

  def formatCode(e: ReactEventI): Callback = formatCode()
  def formatCode(): Callback =
    scope.state.flatMap(
      state =>
        if (state.inputsHasChanged) {
          Callback.future(
            ApiClient[AutowireApi]
              .format(
                FormatRequest(state.inputs.code, state.inputs.worksheetMode)
              )
              .call()
              .map {
                case FormatResponse(Right(formattedCode)) =>
                  scope.modState { s =>
                    // avoid overriding user's code if he/she types while it's formatting
                    if (s.inputs.code == state.inputs.code)
                      s.clearOutputs.setCode(formattedCode)
                    else s
                  }
                case FormatResponse(Left(fullStackTrace)) =>
                  scope.modState(
                    _.clearOutputs
                      .setRuntimeError(
                        Some(
                          RuntimeError(
                            message = "Formatting Failed",
                            line = None,
                            fullStack = fullStackTrace
                          )
                        )
                      )
                  )
              }
          )
        } else Callback(())
    )
}
