package com.olegych.scastie
package client

import japgolly.scalajs.react._, vdom.all._, extra.router.RouterCtl

import api._
import autowire._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import org.scalajs.dom
import org.scalajs.dom.{
  EventSource, WebSocket, 
  Event, MessageEvent, ErrorEvent, CloseEvent,
  window
}
import org.scalajs.dom.raw.{HTMLElement, HTMLScriptElement}

import scala.util.{Success, Failure}

import upickle.default.{read => uread, ReadWriter, macroRW => upickleMacroRW}

object App {
  type AttachedDoms = Map[String, HTMLElement]

  case class Props(
      router: Option[RouterCtl[Page]],
      snippetId: Option[SnippetId],
      embedded: Option[EmbededOptions]
  ) {
    def isEmbedded: Boolean = embedded.isDefined
  }

  object State {
    def default = State(
      view = View.Editor,
      running = false,
      eventSource = None,
      websocket = None,
      isShowingHelpAtStartup = true,
      isHelpModalClosed = false,
      isDarkTheme = true,
      consoleIsOpen = false,
      consoleHasUserOutput = false,
      inputsHasChanged = false,
      snippetId = None,
      loadSnippet = true,
      isStartup = true,
      loadScalaJsScript = false,
      user = None,
      attachedDoms = Map(),
      inputs = Inputs.default,
      outputs = Outputs.default
    )

    implicit val dontSerializeAttachedDoms: ReadWriter[AttachedDoms] = dontSerializeMap[String, HTMLElement]
    implicit val dontSerializeWebSocket: ReadWriter[Option[WebSocket]] = dontSerializeOption[WebSocket]
    implicit val dontSerializeEventSource: ReadWriter[Option[EventSource]] = dontSerializeOption[EventSource]
    implicit val pkl: ReadWriter[State] = upickleMacroRW[State]
  }

  case class State(
      view: View,
      running: Boolean,
      eventSource: Option[EventSource],
      websocket: Option[WebSocket],
      isShowingHelpAtStartup: Boolean,
      isHelpModalClosed: Boolean,
      isDarkTheme: Boolean,
      consoleIsOpen: Boolean,
      consoleHasUserOutput: Boolean,
      inputsHasChanged: Boolean,
      snippetId: Option[SnippetId],
      loadSnippet: Boolean,
      isStartup: Boolean,
      loadScalaJsScript: Boolean,
      user: Option[User],
      attachedDoms: AttachedDoms,
      inputs: Inputs,
      outputs: Outputs
  ) {
    def copyAndSave(view: View = view,
                    running: Boolean = running,
                    eventSource: Option[EventSource] = eventSource,
                    websocket: Option[WebSocket] = websocket,
                    isShowingHelpAtStartup: Boolean = isShowingHelpAtStartup,
                    isHelpModalClosed: Boolean = isHelpModalClosed,
                    isDarkTheme: Boolean = isDarkTheme,
                    consoleIsOpen: Boolean = consoleIsOpen,
                    consoleHasUserOutput: Boolean = consoleHasUserOutput,
                    inputsHasChanged: Boolean = inputsHasChanged,
                    snippetId: Option[SnippetId] = snippetId,
                    user: Option[User] = user,
                    attachedDoms: AttachedDoms = attachedDoms,
                    inputs: Inputs = inputs,
                    outputs: Outputs = outputs): State = {

      val state0 =
        copy(view,
             running,
             eventSource,
             websocket,
             isShowingHelpAtStartup,
             isHelpModalClosed,
             isDarkTheme,
             consoleIsOpen,
             consoleHasUserOutput,
             inputsHasChanged,
             snippetId,
             loadSnippet,
             isStartup,
             loadScalaJsScript,
             user,
             attachedDoms,
             inputs.copy(
               showInUserProfile = false,
               forked = None
             ),
             outputs)

      LocalStorage.save(state0)

      state0
    }

    def isClearable: Boolean =
      outputs.isClearable

    def setRunning(running: Boolean) = {
      val console = !running && !consoleHasUserOutput
      copyAndSave(running = running, consoleIsOpen = !console)
    }

    def toggleTheme =
      copyAndSave(isDarkTheme = !isDarkTheme)

    def toggleConsole =
      copyAndSave(consoleIsOpen = !consoleIsOpen)

    def toggleWorksheetMode =
      copyAndSave(
        inputs = inputs.copy(worksheetMode = !inputs.worksheetMode),
        inputsHasChanged = true
      )

    def toggleHelpAtStartup =
      copyAndSave(isShowingHelpAtStartup = !isShowingHelpAtStartup)

    def closeHelp = 
      resetOutputs
        .copyAndSave(isHelpModalClosed = true)
        .copy(isStartup = false)
    
    def showHelp = copy(isHelpModalClosed = false)
    
    def openConsole =
      copyAndSave(consoleIsOpen = true)

    def setUserOutput =
      copyAndSave(consoleHasUserOutput = true)

    def log(lines: Seq[String]): State =
      copyAndSave(outputs = outputs.copy(console = outputs.console ++ lines))

    def log(line: String): State =
      log(Seq(line))

    def log(line: Option[String]): State =
      line match {
        case Some(l) => log(l + "\n")
        case None => this
      }

    def setLoadSnippet(value: Boolean) = copy(loadSnippet = value)

    def setUser(user: Option[User]) =
      copyAndSave(user = user)

    def setCode(code: String) =
      copyAndSave(
        inputs = inputs.copy(code = code),
        inputsHasChanged = true
      )

    def setInputs(inputs: Inputs) =
      copyAndSave(
        inputs = inputs
      )

    def setSbtConfigExtra(config: String) =
      copyAndSave(
        inputs = inputs.copy(sbtConfigExtra = config),
        inputsHasChanged = true
      )

    def setCleanInputs =
      copyAndSave(inputsHasChanged = false)

    def setView(newView: View) =
      copyAndSave(view = newView)

    def setTarget(target: ScalaTarget) =
      copyAndSave(
        inputs = inputs.copy(target = target),
        inputsHasChanged = true
      )

    def addScalaDependency(scalaDependency: ScalaDependency,
                           project: Project) =
      copyAndSave(
        inputs = inputs.addScalaDependency(scalaDependency, project),
        inputsHasChanged = true
      )

    def removeScalaDependency(scalaDependency: ScalaDependency) =
      copyAndSave(
        inputs = inputs.removeScalaDependency(scalaDependency),
        inputsHasChanged = true
      )

    def updateDependencyVersion(scalaDependency: ScalaDependency,
                                version: String) = {
      val newScalaDependency = scalaDependency.copy(version = version)
      copyAndSave(
        inputs = inputs.copy(
          libraries = (inputs.libraries - scalaDependency) + newScalaDependency
        ),
        inputsHasChanged = true
      )
    }

    def resetOutputs =
      copyAndSave(outputs = Outputs.default,
                  consoleIsOpen = false,
                  consoleHasUserOutput = false,
                  attachedDoms = Map())

    def setRuntimeError(runtimeError: Option[RuntimeError]) =
      if (runtimeError.isEmpty) this
      else copyAndSave(outputs = outputs.copy(runtimeError = runtimeError))

    def addProgress(progress: SnippetProgress) = {
      val state =
        addOutputs(progress.compilationInfos, progress.instrumentations)
          .log(progress.userOutput)
          .log(progress.sbtOutput)
          .setForcedProgramMode(progress.forcedProgramMode)
          .setRunning(!progress.done)
          .setLoadScalaJsScript(loadScalaJsScript | progress.done)
          .setRuntimeError(progress.runtimeError)

      if (!progress.userOutput.isEmpty) state.setUserOutput
      else state
    }

    def setProgresses(progresses: List[SnippetProgress]) = {
      progresses.foldLeft(this) {
        case (state, progress) => state.addProgress(progress)
      }
    }

    def setSnippetId(snippetId: SnippetId) = {
      copyAndSave(snippetId = Some(snippetId))
    }

    private def info(message: String) = Problem(api.Info, None, message)

    def setForcedProgramMode(forcedProgramMode: Boolean) = {
      if (!forcedProgramMode) this
      else {
        copyAndSave(
          outputs = outputs.copy(
            compilationInfos = outputs.compilationInfos +
                info("You don't need a main method (or extends App) in Worksheet Mode")
          ))
      }
    }

    def setLoadScalaJsScript(value: Boolean) = {s
      copy(loadScalaJsScript = value)
    }

    def addOutputs(compilationInfos: List[api.Problem],
                   instrumentations: List[api.Instrumentation]) = {

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

      copyAndSave(
        outputs = outputs.copy(
          compilationInfos = outputs.compilationInfos ++ compilationInfos.toSet ++ useWorksheetModeTip,
          instrumentations = outputs.instrumentations ++ instrumentations.toSet
        ))
    }
  }

  class Backend(scope: BackendScope[Props, State]) {

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

    def start(props: Props): Callback = {
      console.log("== Welcome to Scastie ==")

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

  val component =
    ReactComponentB[Props]("App")
      .initialState(LocalStorage.load.getOrElse(State.default))
      .backend(new Backend(_))
      .renderPS {
        case (scope, props, state) => {
          val theme =
            if (state.isDarkTheme) "dark"
            else "light"

          val sideBar =
            if (!props.isEmbedded) TagMod(SideBar(state, scope.backend, props.snippetId))
            else EmptyTag

          val appClass =
            if (!props.isEmbedded) "app"
            else "app embedded"

          div(`class` := s"$appClass $theme")(
            sideBar,
            MainPannel(state, scope.backend, props)
          )
        }
      }
      .componentWillMount(s => s.backend.start(s.props))
      .componentDidUpdate{ v =>
        val state = v.prevState
        val scope = v.$

        // because we use scope.direct in those we dont have time to unset loadScalaJsScript.
        // we use this check to ensure we load only once
        val progressJustClosed = state.eventSource.isEmpty && state.websocket.isEmpty

        if(scope.accessDirect.state.loadScalaJsScript && 
           state.inputs.target.targetType == ScalaTargetType.JS &&
           state.snippetId.isDefined &&
           progressJustClosed) {
          
          scope.accessDirect.modState(_.setLoadScalaJsScript(false))

          Callback {
            val scalaJsId = "scastie-scalajs-playground-target"
            val scalaJsRunId = "scastie-scalajs-playground-run"

            def scalaJsUrl(snippetId: SnippetId): String = {
              val middle =
                snippetId match {
                  case SnippetId(uuid, None) => uuid
                  case SnippetId(uuid, Some(SnippetUserPart(login, update))) => 
                    s"$login/$uuid/${update.getOrElse(0)}"
                }

              s"/${Shared.scalaJsHttpPathPrefix}/$middle/${ScalaTarget.Js.targetFilename}"
            }

            def getOrCreateScript(id: String) = {
              Option(dom.document.getElementById(id).asInstanceOf[HTMLScriptElement]).getOrElse{
                val newScript = dom.document.createElement("script").asInstanceOf[HTMLScriptElement]
                newScript.id = id
                dom.document.body.appendChild(newScript)
                newScript
              }
            }

            val scalaJsScriptElement = getOrCreateScript(scalaJsId)
            scalaJsScriptElement.onload = { (e: dom.Event) =>
              val scalaJsRunScriptElement = getOrCreateScript(scalaJsRunId)
              scalaJsRunScriptElement.defer = true
              scalaJsRunScriptElement.innerHTML =
                """|com.olegych.scastie.client.ClientMain().signal(
                   |  function(){ return Main().result; },
                   |  function(){ return Main().attachedElements; }
                   |)""".stripMargin
            }
            scalaJsScriptElement.src = scalaJsUrl(state.snippetId.get)

          }
        } else Callback(()) 
      }
      .componentWillReceiveProps{ v =>
        val next = v.nextProps.snippetId
        val current = v.currentProps.snippetId
        val state = v.$.state
        val backend = v.$.backend
        

        val setTitle =
          if(state.inputsHasChanged) {
            Callback(dom.document.title = "Scastie (*)") 
          } else {
            Callback(dom.document.title = "Scastie") 
          }

        val loadNewSnippet =
          if(next != current) {
            next match {
              case Some(snippetId) => 
                backend.loadSnippet(snippetId) >>
                backend.setView(View.Editor)
              case _ => Callback(())
            }
            
          } else Callback(())

        loadNewSnippet >> setTitle
      }
      .build

  def apply(props: Props) = component(props)
}
