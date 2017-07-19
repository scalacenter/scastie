package com.olegych.scastie
package client

import components.Scastie

import api._
import japgolly.scalajs.react._, vdom.all._, extra.StateSnapshot,
component.Scala.BackendScope

import scalajs.concurrent.JSExecutionContext.Implicits.queue

import autowire._
import upickle.default.{read => uread}

import org.scalajs.dom._

import scala.util.{Failure, Success}
import scala.concurrent.Future

class ScastieBackend(scope: BackendScope[Scastie, ScastieState]) {
  scope.props.map(_.router)
  scope.props.map(_.snippetId)
  scope.props.map(_.oldSnippetId)
  scope.props.map(_.embedded)
  scope.props.map(_.targetType)

  Global.subscribe(scope)

  def goHome: Callback = {
    scope.props.flatMap(
      props => props.router.fold(Callback.empty)(_.set(Home))
    ) >>
      scope.modState(_.setView(View.Editor))
  }

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

  private val connectedMessage = "Waiting for sbt."

  private def connectEventSource(snippetId: SnippetId) =
    CallbackTo[EventSource] {
      val direct = scope.withEffectsImpure

      val eventSource = new EventSource(snippetUri(snippetId, "progress-sse"))

      def onopen(e: Event): Unit = {
        direct.modState(_.logSystem(connectedMessage))
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
    val direct = scope.withEffectsImpure

    def onopen(e: Event): Unit = {
      direct.modState(_.logSystem(connectedMessage))
    }

    def onmessage(e: MessageEvent): Unit = {
      val progress = uread[SnippetProgress](e.data.toString)
      direct.modState(_.addProgress(progress))
    }

    def onerror(e: ErrorEvent): Unit = {
      direct.modState(_.logSystem(s"Error: ${e.message}"))
    }

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

  private def connectStatusEventSource: CallbackTo[EventSource] =
    CallbackTo[EventSource] {
      val direct = scope.withEffectsImpure
      val eventSource = new EventSource("/status-sse")

      def onopen(e: Event): Unit = {
        console.log("connect status status open", e)
      }

      def onmessage(e: MessageEvent): Unit = {
        val status = uread[StatusProgress](e.data.toString)
        direct.modState(_.addStatus(status))
      }

      def onerror(e: Event): Unit = {
        console.log(e)
        if (e.eventPhase == EventSource.CLOSED) {
          eventSource.close()
        }
        direct.modState(_.removeStatus)
      }

      eventSource.onopen = onopen _
      eventSource.onmessage = onmessage _
      eventSource.onerror = onerror _
      eventSource
    }

  def connectStatus: Callback = {
    connectStatusEventSource.attemptTry.flatMap {
      case Success(eventSource) => {
        scope.modState(
          _.copy(statusEventSource = Some(eventSource))
        )
      }
      case Failure(errorEventSource) => {
        Callback(println(errorEventSource))
      }
    }
  }

  private def setHome = scope.props.flatMap(
    _.router
      .map(_.set(Home))
      .getOrElse(Callback.empty)
  )

  def resetBuild: Callback = {
    val setData = scope.modState(
      state =>
        state
          .setInputs(Inputs.default.copy(code = state.inputs.code))
          .clearOutputs
          .clearSnippetId
          .setChangedInputs
    )

    setData >> setHome
  }

  def newSnippet: Callback = {
    val setData = scope.modState(
      _.setInputs(Inputs.default.copy(code = "")).clearOutputs.clearSnippetId.setChangedInputs
    )

    setData >> setHome
  }

  def clear: Callback = scope.modState(_.clearOutputs)

  def clearCode: Callback = scope.modState(_.setCode(""))

  def setView(newView: View): Callback =
    scope.modState(_.setView(newView))

  val viewSnapshot = StateSnapshot.withReuse.prepare(setView)

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

  def toggleTheme: Callback = scope.modState(_.toggleTheme)
  def toggleLineNumbers: Callback = scope.modState(_.toggleLineNumbers)
  def togglePresentationMode: Callback =
    scope.modState(_.togglePresentationMode)

  def openConsole: Callback = scope.modState(_.openConsole)
  def closeConsole: Callback = scope.modState(_.closeConsole)
  def toggleConsole: Callback = scope.modState(_.toggleConsole)

  def openResetModal: Callback = scope.modState(_.openResetModal)
  def closeResetModal: Callback = scope.modState(_.closeResetModal)

  def openNewSnippetModal: Callback = scope.modState(_.openNewSnippetModal)
  def closeNewSnippetModal: Callback = scope.modState(_.closeNewSnippetModal)

  def openHelpModal: Callback = scope.modState(_.openHelpModal)
  def closeHelpModal: Callback = scope.modState(_.closeHelpModal)

  def openWelcomeModal: Callback = scope.modState(_.openWelcomeModal)
  def closeWelcomeModal: Callback = scope.modState(_.closeWelcomeModal)

  def closeShareModal: Callback = scope.modState(_.closeShareModal)

  def openShareModal(snippetId: Option[SnippetId]): Callback =
    scope.modState(_.openShareModal(snippetId))

  def openShareModal(snippetId: SnippetId): Callback =
    scope.modState(_.openShareModal(Some(snippetId)))

  def forceDesktop: Callback = scope.modState(_.forceDesktop)

  def toggleWorksheetMode: Callback = scope.modState(_.toggleWorksheetMode)

  private def connectProgress(snippetId: SnippetId): Callback = {
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
  }

  def run: Callback = {
    scope.state.flatMap(
      state =>
        if (!state.isScalaJsScriptLoaded || state.inputsHasChanged) {
          Callback.future(
            ApiClient[AutowireApi]
              .run(state.inputs)
              .call()
              .map(connectProgress)
          )
        } else {
          scope.modState(_.setIsReRunningScalaJs(true))
      }
    )
  }

  private def saveCallback(sId: SnippetId): Callback = {
    val setState =
      scope.modState(
        _.setSnippetSaved(true)
          .setSnippetId(sId)
          .setLoadSnippet(false)
          .clearOutputs
      )

    val page = Page.fromSnippetId(sId)
    val setUrl =
      scope.props.flatMap(
        _.router.map(_.set(page)).getOrElse(Callback.empty)
      )

    setState >> setUrl
  }

  def save: Callback = {
    scope.state.flatMap(
      state =>
        Callback.unless(state.isSnippetSaved)(
          Callback.future(
            ApiClient[AutowireApi]
              .save(state.inputs)
              .call()
              .map(saveCallback)
          )
      )
    )
  }

  def saveOrUpdate: Callback = {
    scope.props.flatMap(
      props =>
        props.snippetId match {
          case Some(snippetId) => update(snippetId)
          case None            => save
      }
    )
  }

  def amend(snippetId: SnippetId): Callback =
    scope.state.flatMap(
      state =>
        Callback.unless(state.isSnippetSaved)(
          Callback.future(
            ApiClient[AutowireApi]
              .amend(snippetId, state.inputs)
              .call()
              .map(
                success =>
                  if (success) saveCallback(snippetId)
                  else Callback(window.alert("Failed to amend"))
              )
          )
      )
    )

  def fork(snippetId: SnippetId): Callback =
    scope.state.flatMap(
      state =>
        Callback.unless(state.isSnippetSaved)(
          Callback.future(
            ApiClient[AutowireApi]
              .fork(snippetId, state.inputs)
              .call()
              .map {
                case Some(sId) => saveCallback(sId)
                case None      => Callback(window.alert("Failed to fork"))
              }
          )
      )
    )

  def update(snippetId: SnippetId): Callback =
    scope.state.flatMap(
      state =>
        Callback.unless(state.isSnippetSaved)(
          Callback.future(
            ApiClient[AutowireApi]
              .update(snippetId, state.inputs)
              .call()
              .map {
                case Some(sId) => saveCallback(sId)
                case None      => Callback(window.alert("Failed to update"))
              }
          )
      )
    )

  def loadOldSnippet(id: Int): Callback = {
    loadSnippetBase(
      ApiClient[AutowireApi]
        .fetchOld(id)
        .call()
    )
  }

  def loadSnippet(snippetId: SnippetId): Callback = {
    loadSnippetBase(
      ApiClient[AutowireApi]
        .fetch(snippetId)
        .call(),
      afterLoading = _.setSnippetId(snippetId)
    ) >> connectProgress(snippetId)
  }

  def loadSnippetBase(
      fetchSnippet: => Future[Option[FetchResult]],
      afterLoading: ScastieState => ScastieState = identity
  ): Callback = {
    scope.state.flatMap(
      state =>
        if (state.loadSnippet) {
          val loadStateFromApi =
            Callback.future(
              fetchSnippet.map {
                case Some(FetchResult(inputs, progresses)) => {
                  loadStateFromLocalStorage >>
                    clear >>
                    scope.modState(
                      state =>
                        afterLoading(
                          state
                            .setInputs(inputs)
                            .setProgresses(progresses)
                            .setCleanInputs
                      )
                    )
                }
                case _ =>
                  scope.modState(_.setCode(s"//snippet not found"))
              }
            )
          loadStateFromApi >>
            setView(View.Editor) >>
            scope.modState(_.clearOutputs.closeModals)

        } else {
          scope.modState(_.setLoadSnippet(true))
      }
    )
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
      .getOrElse(Callback.empty)

  def start(props: Scastie): Callback = {
    def loadUser: Callback =
      Callback.future(
        ApiClient[AutowireApi]
          .fetchUser()
          .call()
          .map(result => scope.modState(_.setUser(result)))
      )

    val initialState =
      props.embedded match {
        case None => {
          props.snippetId match {
            case Some(snippetId) => loadSnippet(snippetId)
            case None =>
              props.oldSnippetId match {
                case Some(id) => loadOldSnippet(id)
                case None     => loadStateFromLocalStorage
              }
          }
        }
        case Some(embededOptions) => {
          embededOptions match {
            case EmbededOptions(Some(snippetId), _) => loadSnippet(snippetId)
            case EmbededOptions(_, Some(inputs)) =>
              scope.modState(_.setInputs(inputs))
            case _ => Callback.empty
          }
        }
      }

    initialState >> loadUser
  }

  def formatCode: Callback =
    scope.state.flatMap(
      state =>
        Callback.when(state.inputsHasChanged)(
          Callback.future(
            ApiClient[AutowireApi]
              .format(
                FormatRequest(state.inputs.code,
                              state.inputs.worksheetMode,
                              state.inputs.target.targetType)
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
      )
    )

  def completeCodeAt(pos: Int): Callback = {
    scope.state.flatMap(
      state => {
        // we only autocomplete for the default configuration
        // https://github.com/scalacenter/scastie/issues/275
        if (state.inputs.copy(code = Inputs.default.code) != Inputs.default) {
          Callback()
        } else {
          Callback.future(
            ApiClient[AutowireApi]
              .complete(
                CompletionRequest(EnsimeRequestInfo(state.inputs, pos))
              )
              .call()
              .map {
                case Some(response) =>
                  scope.modState(_.setCompletions(response.completions))
                case _ =>
                  Callback()
              }
          )
        }

      }
    )
  }

  def typeAt(token: String, pos: Int): Callback = {
    scope.state.flatMap(
      state => {
        Callback.future(
          ApiClient[AutowireApi]
            .typeAt(
              TypeAtPointRequest(EnsimeRequestInfo(state.inputs, pos))
            )
            .call()
            .map {
              case Some(response) =>
                scope.modState(
                  _.setTypeAtInto(
                    Some(
                      TypeInfoAt(
                        token = token,
                        typeInfo = response.typeInfo
                      )
                    )
                  )
                )
              case _ =>
                Callback()
            }
        )
      }
    )
  }

  def clearCompletions(): Callback = {
    scope.modState(_.setCompletions(List()))
  }
}
