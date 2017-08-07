package com.olegych.scastie.client

import components.Scastie

import play.api.libs.json.Json

import com.olegych.scastie.api._
import japgolly.scalajs.react._, vdom.all._, extra.StateSnapshot,
component.Scala.BackendScope

import scalajs.concurrent.JSExecutionContext.Implicits.queue

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
    EventStream.connect(
      eventSourceUri = s"/progress-sse/${snippetId.url}",
      websocketUri = s"/progress-ws/${snippetId.url}",
      handler = new EventStreamHandler[SnippetProgress] {
        val direct = scope.withEffectsImpure

        def onMessage(progress: SnippetProgress): Boolean = {
          direct.modState(_.addProgress(progress))
          progress.done
        }

        def onOpen(): Unit =
          direct.modState(_.logSystem("Connected. Waiting for sbt"))

        def onError(error: String): Unit =
          direct.modState(_.logSystem(s"Error: $error"))

        def onClose(reason: Option[String]): Unit = {
          val msg = reason.map(": " + _).getOrElse(".")
          direct.modState(
            _.copy(
              isRunning = false,
              progressStream = None
            ).logSystem("Closed" + msg)
          )
        }

        def onErrorC(error: String): Callback =
          scope.modState(_.logSystem(s"Error: $error"))

        def onConnected(stream: EventStream[SnippetProgress]): Callback =
          scope.modState(
            _.run(snippetId)
              .copy(progressStream = Some(stream))
          )
      }
    )
  }

  def connectStatus: Callback = {
    EventStream.connect(
      eventSourceUri = "/status-sse",
      websocketUri = "/status-ws",
      handler = new EventStreamHandler[StatusProgress] {
        val direct = scope.withEffectsImpure

        def onMessage(status: StatusProgress): Boolean = {
          direct.modState(_.addStatus(status))
          false
        }

        def onOpen(): Unit =
          console.log("connect status open")

        def onError(error: String): Unit =
          console.log("connect status error: " + error)

        def onClose(reason: Option[String]): Unit =
          direct.modState(_.removeStatus)

        def onErrorC(error: String): Callback =
          Callback(onError(error))

        def onConnected(stream: EventStream[StatusProgress]): Callback =
          scope.modState(
            _.copy(statusStream = Some(stream))
          )
      }
    )
  }

  def run: Callback = {
    scope.state.flatMap(
      state =>
        if (!state.isScalaJsScriptLoaded || state.inputsHasChanged) {
          Callback.future(
            ApiClient
              .run(state.inputs)
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
            ApiClient
              .save(state.inputs)
              .map(saveCallback)
          )
      )
    )
  }

  def saveOrUpdate: Callback = {
    scope.state.flatMap(
      state =>
        scope.props.flatMap(
          props =>
            props.snippetId match {
              case Some(snippetId) => {
                if (snippetId.isOwnedBy(state.user)) {
                  update(snippetId)
                } else {
                  fork(snippetId)
                }
              }

              case None => save
          }
      )
    )
  }

  def amend(snippetId: SnippetId): Callback =
    scope.state.flatMap(
      state =>
        Callback.unless(state.isSnippetSaved)(
          Callback.future(
            ApiClient
              .amend(EditInputs(snippetId, state.inputs))
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
        Callback
          .future(
            ApiClient
              .fork(EditInputs(snippetId, state.inputs))
              .map {
                case Some(sId) => saveCallback(sId)
                case None      => Callback(window.alert("Failed to fork"))
              }
          )
          .when_(state.isSnippetSaved)
    )

  def update(snippetId: SnippetId): Callback =
    scope.state.flatMap(
      state =>
        Callback
          .future(
            ApiClient
              .update(EditInputs(snippetId, state.inputs))
              .map {
                case Some(sId) => saveCallback(sId)
                case None      => Callback(window.alert("Failed to update"))
              }
          )
          .when_(state.isSnippetSaved)
    )

  def loadOldSnippet(id: Int): Callback = {
    loadSnippetBase(
      ApiClient
        .fetchOld(id)
    )
  }

  def loadSnippet(snippetId: SnippetId): Callback = {
    loadSnippetBase(
      ApiClient
        .fetch(snippetId),
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
                  loadStateFromLocalStorage(isSnippetSaved = true) >>
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

  def loadStateFromLocalStorage(isSnippetSaved: Boolean): Callback =
    LocalStorage.load
      .map(
        state =>
          scope.modState(
            _ =>
              state
                .setRunning(false)
                .setSnippetSaved(isSnippetSaved)
                .resetScalajs
        )
      )
      .getOrElse(Callback.empty)

  def start(props: Scastie): Callback = {
    def loadUser: Callback =
      Callback.future(
        ApiClient
          .fetchUser()
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
                case None     => loadStateFromLocalStorage(isSnippetSaved = false)
              }
          }
        }
        case Some(embededOptions) => {
          embededOptions match {
            case EmbeddedOptions(Some(snippetId), _) => loadSnippet(snippetId)
            case EmbeddedOptions(_, Some(inputs)) =>
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
            ApiClient
              .format(
                FormatRequest(state.inputs.code,
                              state.inputs.worksheetMode,
                              state.inputs.target.targetType)
              )
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
        Callback
          .future(
            ApiClient
              .autocomplete(
                AutoCompletionRequest(EnsimeRequestInfo(state.inputs, pos))
              )
              .map {
                case Some(response) =>
                  scope.modState(_.setCompletions(response.completions))
                case _ =>
                  Callback.empty
              }
          )
      }
    )
  }

  def typeAt(token: String, pos: Int): Callback = {
    scope.state.flatMap(
      state => {
        Callback
          .future(
            ApiClient
              .typeAt(
                TypeAtPointRequest(EnsimeRequestInfo(state.inputs, pos))
              )
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
                  Callback.empty
              }
          )
      }
    )
  }

  def updateEnsimeConfig(): Callback = {
    scope.state.flatMap(
      state => {
        Callback.future(
          ApiClient
            .updateEnsimeConfig(
              UpdateEnsimeConfigRequest(state.inputs)
            )
            .map(_ => Callback.empty)
        )
      }
    )
  }

  def clearCompletions(): Callback = {
    scope.modState(_.setCompletions(List.empty))
  }
}
