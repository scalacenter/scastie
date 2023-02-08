package com.olegych.scastie.client

import java.util.UUID

import com.olegych.scastie.api._
import com.olegych.scastie.client.components.Scastie
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.BackendScope
import japgolly.scalajs.react.extra._
import japgolly.scalajs.react.util.Effect.Id
import org.scalajs.dom.{Position => _, _}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

case class ScastieBackend(scastieId: UUID, serverUrl: Option[String], scope: BackendScope[Scastie, ScastieState]) {

  private val restApiClient =
    new RestApiClient(serverUrl)

  // XXX: This should not be global
  Global.subscribe(scope, scastieId)

  def unsubscribeGlobal: Callback = {
    Callback(Global.subscribe(scope, scastieId))
  }

  val codeChange: String ~=> Callback =
    Reusable.fn(code => scope.modState(_.setCode(code)))

  val sbtConfigChange: String ~=> Callback = {
    Reusable.fn(newConfig => scope.modState(_.setSbtConfigExtra(newConfig)))
  }

  val resetBuild: Reusable[Callback] =
    Reusable.always {
      val setData = scope.state.map(
        state => {
          state
            .setInputs(Inputs.default.copy(code = state.inputs.code))
            .clearOutputs
            .clearSnippetId
            .setChangedInputs
        }
      )

      setData >> setHome
    }

  val newSnippet: Reusable[Callback] =
    Reusable.always {
      val setData = scope.state.map(state => {
        state
          .copy(isDesktopForced = false)
          .setInputs(Inputs.default.copy(code = ""))
          .clearOutputs
          .clearSnippetId
          .setChangedInputs
      })

      setData >> setHome
    }

  val clear: Reusable[Callback] =
    Reusable.always(scope.modState(_.clearOutputsPreserveConsole) >> scope.modState(_.closeModals))

  private def clearOutputs: Callback =
    scope.modState(_.clearOutputs)

  def clearCode: Callback =
    scope.modState(_.setCode(""))

  val setViewReused: View ~=> Callback =
    Reusable.fn(setView _)

  def setView(newView: View): Callback =
    scope.modState(_.setView(newView))

  val viewSnapshot: StateSnapshot.withReuse.FromSetStateFn[View] =
    StateSnapshot.withReuse.prepare((opts, c) => opts.fold(c)(setView))

  val setTarget: ScalaTarget ~=> Callback =
    Reusable.fn(target => scope.modState(_.setTarget(target)))

  val addScalaDependency: (ScalaDependency, Project) ~=> Callback =
    Reusable.fn {
      case (scalaDependency, project) =>
        scope.modState(_.addScalaDependency(scalaDependency, project))
    }

  val removeScalaDependency: ScalaDependency ~=> Callback =
    Reusable.fn(
      scalaDependency => scope.modState(_.removeScalaDependency(scalaDependency))
    )

  val updateDependencyVersion: (ScalaDependency, String) ~=> Callback =
    Reusable.fn {
      case (scalaDependency, version) =>
        scope.modState(_.updateDependencyVersion(scalaDependency, version))
    }

  val toggleTheme: Reusable[Callback] =
    Reusable.always(scope.modState(_.toggleTheme))

  val setMetalsStatus: MetalsStatus ~=> Callback =
    Reusable.fn(status => scope.modState(_.setMetalsStatus(status)))

  val toggleMetalsStatus: Reusable[Callback] =
    Reusable.always(scope.modState(_.toggleMetalsStatus))

  val toggleLineNumbers: Reusable[Callback] =
    Reusable.always(scope.modState(_.toggleLineNumbers))

  val togglePresentationMode: Reusable[Callback] =
    Reusable.always(scope.modState(_.togglePresentationMode))

  val openConsole: Reusable[Callback] =
    Reusable.always(scope.modState(_.openConsole))

  val closeConsole: Reusable[Callback] =
    Reusable.always(scope.modState(_.closeConsole))

  val toggleConsole: Reusable[Callback] =
    Reusable.always(scope.modState(_.toggleConsole))

  val openResetModal: Reusable[Callback] =
    Reusable.always(scope.modState(_.openResetModal))

  val closeResetModal: Reusable[Callback] =
    Reusable.always(scope.modState(_.closeResetModal))

  val openNewSnippetModal: Reusable[Callback] =
    Reusable.always(scope.modState(_.openNewSnippetModal))

  // ok
  private def closeNewSnippetModal0: Callback =
    scope.modState(_.closeNewSnippetModal)

  val closeNewSnippetModal: Reusable[Callback] =
    Reusable.always(closeNewSnippetModal0)

  val openHelpModal: Reusable[Callback] =
    Reusable.always(scope.modState(_.openHelpModal))

  val openPrivacyPolicyModal: Reusable[Callback] =
    Reusable.always(scope.modState(_.openPrivacyPolicyModal))

  val closeHelpModal: Reusable[Callback] =
    Reusable.always(scope.modState(_.toggleHelpModal))

  val closePrivacyPolicyModal: Reusable[Callback] =
    Reusable.always(scope.modState(_.togglePrivacyPolicyModal))

  val closePrivacyPolicyPrompt: Reusable[Callback] =
    Reusable.always(scope.modState(_.setPrivacyPolicyPromptClosed(true)))

  val openPrivacyPolicyPrompt: Reusable[Callback] =
    Reusable.always(scope.modState(_.setPrivacyPolicyPromptClosed(false)))

  val openLoginModal: Reusable[Callback] =
    Reusable.always(scope.modState(_.setLoginModalClosed(false)))

  val closeLoginModal: Reusable[Callback] =
    Reusable.always(scope.modState(_.setLoginModalClosed(true)))

  val toggleHelpModal: Reusable[Callback] =
    Reusable.always(scope.modState(_.toggleHelpModal))

  val closeShareModal: Reusable[Callback] =
    Reusable.always(scope.modState(_.closeShareModal))

  val openShareModalOption: Option[SnippetId] ~=> Callback =
    Reusable.fn(snippetId => scope.modState(_.openShareModal(snippetId)))

  val openShareModal: SnippetId ~=> Callback =
    Reusable.fn(snippetId => scope.modState(_.openShareModal(Some(snippetId))))

  val openEmbeddedModal: Reusable[Callback] =
    Reusable.always(scope.modState(_.openEmbeddedModal))

  val closeEmbeddedModal: Reusable[Callback] =
    Reusable.always(scope.modState(_.closeEmbeddedModal))

  val forceDesktop: Reusable[Callback] =
    Reusable.always(scope.modState(_.forceDesktop))

  val toggleWorksheetMode: Reusable[Callback] =
    Reusable.always(unlessEmbedded(_.toggleWorksheetMode))

  private def unlessEmbedded(f: ScastieState => ScastieState): Callback = {
    scope.props
      .map(_.isEmbedded)
      .flatMap(isEmbedded => scope.modState(f).unless_(isEmbedded))
  }

  private def connectProgress(snippetId: SnippetId): Callback = {
    val apiBase = serverUrl.getOrElse("")

    EventStream.connect(
      eventSourceUri = s"$apiBase/api/progress-sse/${snippetId.url}",
      websocketUri = s"$apiBase/api/progress-ws/${snippetId.url}",
      handler = new EventStreamHandler[SnippetProgress] {
        val direct: scope.WithEffect[Id] = scope.withEffectsImpure

        def onMessage(progress: SnippetProgress): Boolean = {
          direct.modState(_.addProgress(progress))
          progress.isDone
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

        def onConnectionError(error: String): Callback =
          scope.modState(_.logSystem(s"Error: $error"))

        def onConnected(stream: EventStream[SnippetProgress]): Callback =
          scope.modState(
            _.run(snippetId)
              .copy(progressStream = Some(stream))
          )
      }
    )
  }

  def disconnectStatus: Callback = {
    scope.state.map(
      _.statusStream.foreach(_.close(force = true))
    )
  }

  def connectStatus: Callback = {
    val direct = scope.withEffectsImpure

    val apiBase = serverUrl.getOrElse("")

    EventStream.connect(
      eventSourceUri = s"$apiBase/api/status-sse",
      websocketUri = s"$apiBase/api/status-ws",
      handler = new EventStreamHandler[StatusProgress] {
        def onMessage(status: StatusProgress): Boolean = {
          status match {
            case StatusProgress.KeepAlive => this
            case _                        => direct.modState(_.addStatus(status))
          }
          false
        }

        def onOpen(): Unit = {}

        def onError(error: String): Unit = {
          println(s"Status onError: $error")
        }

        def onClose(reason: Option[String]): Unit = {
          console.log(s"Connection to status updates closed: $reason")
          direct.modState(_.removeStatus)
        }

        def onConnectionError(error: String): Callback = {
          console.log(s"Cannot connect to status updates: $error")
          Callback(onError(error))
        }

        def onConnected(stream: EventStream[StatusProgress]): Callback = {
          scope.modState(
            _.copy(statusStream = Some(stream))
          )
        }
      }
    )
  }

  val run: Reusable[Callback] =
    Reusable.always(
      scope.state.flatMap(
        state =>
          Callback.future(
            restApiClient
              .run(state.inputs)
              .map(connectProgress)
        )
      )
    )

  val acceptPolicy: Reusable[Callback] =
    Reusable.always(
      Callback.future {
        restApiClient.acceptPrivacyPolicy().map { result =>
          scope.modState(_.setPrivacyPolicyPromptClosed(result))
        }
      }
    )

  val removeUserFromPolicyStatus: Reusable[Callback] =
    Reusable.always(
      Callback.future {
        restApiClient.removeUserFromPolicyStatus().map { result =>
          scope.modState(_.setPrivacyPolicyPromptClosed(result)).map(_ => {
            if (result) document.location.reload()
          })
        }
      }
    )

  val removeAllUserSnippets: Reusable[Callback] =
    Reusable.always(
      Callback.future {
        restApiClient.removeAllUserSnippets().map(Callback(_))
      }
    )

  val refusePrivacyPolicy: Reusable[Callback] = Reusable.always(
    removeAllUserSnippets >> removeUserFromPolicyStatus
  )

  private def saveCallback(sId: SnippetId): Callback = {
    val setState = scope.modState(_.setCleanInputs.setSnippetId(sId).setLoadSnippet(false))
    val page = Page.fromSnippetId(sId)
    val setUrl = scope.props.map {
      _.router.foreach(r => org.scalajs.dom.window.history.pushState("", "", r.urlFor(page).value))
    }
    connectProgress(sId) >> setState >> setUrl
  }

  private def save0: Callback = {
    scope.state.flatMap { state =>
      Callback.future(restApiClient.save(state.inputs).map(saveCallback))
    }
  }

  val saveBlocking: Reusable[CallbackTo[Option[SnippetId]]] =
    Reusable.always(
      scope.state.map(state => restApiClient.saveBlocking(state.inputs))
    )

  val saveOrUpdate: Reusable[Callback] =
    Reusable.always(
      scope.props.flatMap { props =>
        scope.state
          .flatMap { state =>
            if (props.isEmbedded) {
              run
            } else {
              state.snippetId match {
                case Some(snippetId) =>
                  if (snippetId.isOwnedBy(state.user)) {
                    update0(snippetId)
                  } else {
                    fork0(snippetId)
                  }
                case None => save0
              }
            }
          }
      }
    )

  private def fork0(snippetId: SnippetId): Callback =
    scope.state.flatMap { state =>
      Callback.future(
        restApiClient
          .fork(EditInputs(snippetId, state.inputs))
          .map {
            case Some(sId) => saveCallback(sId)
            case None      => Callback(window.alert("Failed to fork"))
          }
      )
    }

  private def update0(snippetId: SnippetId): Callback =
    scope.state.flatMap { state =>
      Callback.future(
        restApiClient
          .update(EditInputs(snippetId, state.inputs))
          .map {
            case Some(sId) => saveCallback(sId)
            case None      => Callback(window.alert("Failed to update"))
          }
      )
    }

  def loadOldSnippet(id: Int): Callback = {
    loadSnippetBase(
      restApiClient.fetchOld(id)
    )
  }

  def loadSnippet(snippetId: SnippetId): Callback = {
    loadSnippetBase(
      restApiClient.fetch(snippetId),
      afterLoading = _.setSnippetId(snippetId),
      snippetId = Some(snippetId)
    )
  }

  private def loadSnippetBase(
      fetchSnippet: => Future[Option[FetchResult]],
      afterLoading: ScastieState => ScastieState = identity,
      snippetId: Option[SnippetId] = None
  ): Callback = {
    scope.state.flatMap { state =>
      if (state.loadSnippet) {
        val loadStateFromApi =
          Callback.future(
            fetchSnippet.map {
              case Some(FetchResult(inputs, progresses)) =>
                val isDone = progresses.exists(_.isDone)
                val connect =
                  snippetId match {
                    case Some(sid) if !isDone => connectProgress(sid)
                    case _                    => Callback(())
                  }
                clearOutputs >> scope.modState { state =>
                  afterLoading(
                    state
                      .setInputs(inputs)
                      .setProgresses(progresses)
                      .setCleanInputs
                  )
                } >> connect
              case _ =>
                scope.modState(_.setCode(s"//snippet not found"))
            }
          )

        loadStateFromApi >>
          setView(View.Editor) >>
          scope.modState(
            _.clearOutputs.closeModals.copy(inputsHasChanged = false)
          )
      } else {
        scope.modState(_.setLoadSnippet(true))
      }
    }
  }

  def loadUser: Callback =
    Callback.future(
      restApiClient
        .fetchUser()
        .map(result => scope.modState(_.setUser(result)))
    ) >> Callback.future(
      restApiClient
        .getPrivacyPolicyStatus()
        .map(result => scope.modState(_.setPrivacyPolicyPromptClosed(result)))
    )

  val formatCode: Reusable[Callback] = Reusable.always {
    scope.state.flatMap { state =>
      Callback.future {
        restApiClient
          .format(FormatRequest(state.inputs.code, state.inputs.isWorksheetMode, state.inputs.target))
          .map {
            case FormatResponse(Right(formattedCode)) =>
              scope.modState { s =>
                // avoid overriding user's code if he/she types while it's formatting
                if (s.inputs.code == state.inputs.code)
                  s.clearOutputsPreserveConsole.setCode(formattedCode)
                else s
              }
            case FormatResponse(Left(error)) =>
              scope.modState {
                _.setRuntimeError(Some(RuntimeError(message = "Formatting failed: " + error, line = None, fullStack = "")))
              }
          }
      }
    }
  }

  val loadProfile: Reusable[Future[List[SnippetSummary]]] =
    Reusable.always(restApiClient.fetchUserSnippets())

  val deleteSnippet: SnippetId ~=> Future[Boolean] =
    Reusable.always(snippetId => restApiClient.delete(snippetId))

  private def setHome = scope.props.flatMap(
    _.router
      .map(_.set(Home))
      .getOrElse(Callback.empty)
  )
}
