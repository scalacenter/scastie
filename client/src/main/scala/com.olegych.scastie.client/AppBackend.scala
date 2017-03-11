package com.olegych.scastie
package client

import api._

import japgolly.scalajs.react._

import autowire._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import org.scalajs.dom.{
  EventSource, WebSocket, 
  Event, MessageEvent, ErrorEvent, CloseEvent,
  window
}

import upickle.default.{read => uread}

import scala.util.{Success, Failure}


class AppBackend(scope: BackendScope[AppProps, AppState]) {
  Global.subsribe(scope)

  def goHome(e: ReactEventI): Callback = {
    scope.props.flatMap(props =>
      props.router.map(_.set(Home)).getOrElse(Callback(()))
    ) >>
    scope.modState(_.setView(View.Editor))
  }

  def codeChange(newCode: String) =
    scope.modState(_.setCode(newCode))

  def sbtConfigChange(newConfig: String) =
    scope.modState(_.setSbtConfigExtra(newConfig))

  private def snippetUri(snippetId: SnippetId, connectionMethod: String): String = {
    val snippetPart =
      snippetId.user match {
        case Some(SnippetUserPart(login, update)) => {
          val updatePart = update.map(_.toString).getOrElse("")
          s"$login/${snippetId.base64UUID}/$updatePart"
        }
        case None =>  s"${snippetId.base64UUID}"
      }

    s"/$connectionMethod/$snippetPart"
  }

  private def connectEventSource(snippetId: SnippetId) = CallbackTo[EventSource] {
    val direct = scope.accessDirect

    val eventSource = new EventSource(snippetUri(snippetId, "progress-sse"))

    def onopen(e: Event): Unit = {
      direct.modState(_.log("Connected.\n"))
    }
    def onmessage(e: MessageEvent): Unit = {
      val progress = uread[SnippetProgress](e.data.toString)

      direct.modState(_.addProgress(progress))

      if (progress.done) {
        direct.modState(
          _.copy(eventSource = None, running = false)
        )
        eventSource.close()
      }
    }
    def onerror(e: Event): Unit = {
      if (e.eventPhase == EventSource.CLOSED) {
        eventSource.close()
      } else {
        direct.modState(_.log(s"Error: ${e.toString}"))
      }
    }

    eventSource.onopen = onopen _
    eventSource.onmessage = onmessage _
    eventSource.onerror = onerror _
    eventSource
  }

  private def connectWebSocket(snippetId: SnippetId) = CallbackTo[WebSocket] {
    val direct = scope.accessDirect

    def onopen(e: Event): Unit = direct.modState(_.log("Connected.\n"))
    def onmessage(e: MessageEvent): Unit = {
      val progress = uread[SnippetProgress](e.data.toString)
      direct.modState(_.addProgress(progress))
    }
    def onerror(e: ErrorEvent): Unit =
      direct.modState(_.log(s"Error: ${e.message}"))
    def onclose(e: CloseEvent): Unit =
      direct.modState(
        _.copy(websocket = None, running = false)
          .log(s"Closed: ${e.reason}\n"))

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

  def clear(e: ReactEventI): Callback = clear()
  def clear(): Callback = scope.modState(_.resetOutputs)

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
  def toggleConsole(e: ReactEventI): Callback = toggleConsole()

  def toggleHelpAtStartup(): Callback =
    scope.modState(_.toggleHelpAtStartup)

  def closeHelp(): Callback =
    scope.modState(_.closeHelp)

  def showHelp(e: ReactEventI): Callback =
    scope.modState(_.showHelp)

  def toggleWorksheetMode(): Callback =
    scope.modState(_.toggleWorksheetMode)

  def toggleWorksheetMode(e: ReactEventI): Callback =
    toggleWorksheetMode()

  def run(e: ReactEventI): Callback = run()
  def run(): Callback = {
    scope.state.flatMap(s =>
      Callback.future(ApiClient[Api].run(s.inputs).call().map(snippetId =>
        connectEventSource(snippetId).attemptTry.flatMap {
          case Success(eventSource) => {
            scope.modState(
              _.resetOutputs
                .setSnippetId(snippetId)
                .setRunning(true)
                .copy(eventSource = Some(eventSource))
                .log("Connecting...\n")
            )
          }
          case Failure(errorEventSource) =>
            connectWebSocket(snippetId).attemptTry.flatMap {
              case Success(websocket) => {
                scope.modState(
                  _.resetOutputs
                    .setSnippetId(snippetId)
                    .setRunning(true)
                    .copy(websocket = Some(websocket))
                    .log("Connecting...\n")
                )
              }
              case Failure(errorWebSocket) =>
                scope.modState(
                  _.resetOutputs
                    .log(errorEventSource.toString)
                    .log(errorWebSocket.toString)
                    .setRunning(false)
                )
            }
        }
      ))
    )
  }
  
  def save(e: ReactEventI): Callback = save()
  def save(): Callback = {
    scope.state.flatMap(state =>
      if(state.inputsHasChanged) {
        scope.modState(_.setLoadSnippet(false).setCleanInputs) >>
        Callback.future(ApiClient[Api].save(state.inputs).call().map(snippetId =>
          scope.props.flatMap(props =>
            props.router.map(_.set(Page.fromSnippetId(snippetId))).getOrElse(Callback(()))
          )
        ))
      } else Callback(())
    )
  }

  def saveOrUpdate(): Callback = {
    scope.props.flatMap(props =>
      props.snippetId match {
        case Some(snippetId) => update2(snippetId)
        case None => save()
      }
    )
  }

  def amend(snippetId: SnippetId)(e: ReactEventI): Callback = {
    scope.state.flatMap(state =>
      Callback.future(
        ApiClient[Api]
          .amend(snippetId, state.inputs)
          .call()
          .map(success =>
            if(success) Callback(())
            else Callback(window.alert("Failed to amend"))              
          )
      )
    )
  }

  def fork(snippetId: SnippetId)(e: ReactEventI): Callback = {
    scope.state.flatMap(state =>
      Callback.future(
        ApiClient[Api]
          .fork(snippetId, state.inputs)
          .call()
          .map(result =>
            result match {
              case Some(snippetId) => {
                val page = Page.fromSnippetId(snippetId)

                scope.modState(_.setLoadSnippet(false).resetOutputs) >>
                scope.props.flatMap(_.router.map(_.set(page)).getOrElse(Callback(())))
              }

              case None => Callback(window.alert("Failed to fork"))
            }
          )
      )
    )
  }

  def update(snippetId: SnippetId)(e: ReactEventI): Callback = update2(snippetId)
  def update2(snippetId: SnippetId): Callback = {
    scope.state.flatMap(state =>
      Callback.future(
        ApiClient[Api]
          .update(snippetId, state.inputs)
          .call()
          .map(result =>
            result match {
              case Some(snippetId) => {
                val page = Page.fromSnippetId(snippetId)
                scope.modState(_.setLoadSnippet(false).resetOutputs) >>
                scope.props.flatMap(_.router.map(_.set(page)).getOrElse(Callback(())))
              }

              case None => Callback(window.alert("Failed to update"))
            }
          )
      )
    )
  }
  
  def loadSnippet(snippetId: SnippetId): Callback = {
    scope.state.flatMap(state =>
      if(state.loadSnippet) {
        Callback.future(
          ApiClient[Api]
            .fetch(snippetId)
            .call()
            .map(result =>
              result match {
                case Some(FetchResult(inputs, progresses)) => {
                  loadStateFromLocalStorage >>
                  clear() >>
                  scope.modState(
                    _.setInputs(inputs).setProgresses(progresses).setCleanInputs
                  )
                }
                case _ => {
                  scope.modState(_.setCode(s"//snippet not found"))
                }
            })
        )
      } else {
        scope.modState(_.setLoadSnippet(true))
      }
    ) >> scope.modState(_.setSnippetId(snippetId))
  }

  def loadStateFromLocalStorage =
    LocalStorage.load
      .map(state => scope.modState(_ => state.setRunning(false).setLoadScalaJsScript(true)))
      .getOrElse(Callback(()))

  def start(props: AppProps): Callback = {
    def loadUser(): Callback = {
      Callback.future(
        ApiClient[Api]
          .fetchUser()
          .call()
          .map(result =>
            scope.modState(_.setUser(result))
          )
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
    scope.state.flatMap(state =>
      if(state.inputsHasChanged) {
        Callback.future(
          ApiClient[Api]
            .format(FormatRequest(state.inputs.code, state.inputs.worksheetMode))
            .call()
            .map {
              case FormatResponse(Right(formattedCode)) =>
                scope.modState { s =>
                  // avoid overriding user's code if he/she types while it's formatting
                  if (s.inputs.code == state.inputs.code)
                    s.resetOutputs.setCode(formattedCode)
                  else s
                }
              case FormatResponse(Left(fullStackTrace)) =>
                scope.modState(
                  _.resetOutputs.setRuntimeError(
                    Some(
                      RuntimeError(message = "Formatting Failed",
                                   line = None,
                                   fullStack = fullStackTrace))
                  ))
            }
        )
      } else Callback(())
    )
}
