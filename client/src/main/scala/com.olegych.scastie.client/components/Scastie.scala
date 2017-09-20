package com.olegych.scastie.client.components

import com.olegych.scastie.api._
import com.olegych.scastie.client.{
  EmbeddedOptions,
  LocalStorage,
  Page,
  ScastieBackend,
  ScastieState,
  View
}
import japgolly.scalajs.react._, vdom.all._, extra.router._, extra._

import japgolly.scalajs.react.component.builder.Lifecycle.RenderScope
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLScriptElement

import scalajs.js.timers

import java.util.UUID

final case class Scastie(scastieId: UUID,
                         router: Option[RouterCtl[Page]],
                         snippetId: Option[SnippetId],
                         oldSnippetId: Option[Int],
                         embedded: Option[EmbeddedOptions],
                         targetType: Option[ScalaTargetType]) {

  @inline def render = Scastie.component(serverUrl, scastieId)(this)

  def serverUrl: Option[String] = embedded.map(_.serverUrl)
  def isEmbedded: Boolean = embedded.isDefined
}

object Scastie {
  def default(router: RouterCtl[Page]): Scastie =
    Scastie(
      scastieId = UUID.randomUUID(),
      router = Some(router),
      snippetId = None,
      oldSnippetId = None,
      embedded = None,
      targetType = None
    )

  private def setTitle(state: ScastieState, props: Scastie) =
    if (!props.isEmbedded) {
      if (state.inputsHasChanged) {
        Callback(dom.document.title = "Scastie (*)")
      } else {
        Callback(dom.document.title = "Scastie")
      }
    } else {
      Callback(())
    }

  private def render(
      scope: RenderScope[Scastie, ScastieState, ScastieBackend],
      props: Scastie,
      state: ScastieState
  ): VdomElement = {
    val theme =
      if (state.isDarkTheme) "dark"
      else "light"

    val forceDesktopClass =
      (cls := "force-desktop").when(state.isDesktopForced)

    div(cls := s"app $theme", forceDesktopClass)(
      SideBar(
        isDarkTheme = state.isDarkTheme,
        status = state.status,
        inputs = state.inputs,
        ensimeConfigurationLoading = state.ensimeConfigurationLoading,
        serverUrl = props.serverUrl,
        toggleTheme = scope.backend.toggleTheme,
        view = scope.backend.viewSnapshot(state.view),
        openHelpModal = scope.backend.openHelpModal,
        updateEnsimeConfig = scope.backend.updateEnsimeConfig
      ).render.unless(props.isEmbedded || state.isPresentationMode),
      MainPanel(
        state,
        scope.backend,
        props
      ).render,
      WelcomeModal(
        isClosed = state.modalState.isWelcomeModalClosed,
        close = scope.backend.closeWelcomeModal
      ).render,
      HelpModal(
        isClosed = state.modalState.isHelpModalClosed,
        close = scope.backend.closeHelpModal
      ).render
    )
  }

  private def component(serverUrl: Option[String], scastieId: UUID) =
    ScalaComponent
      .builder[Scastie]("Scastie")
      .initialStateFromProps { props =>
        val state = {
          val loadedState =
            LocalStorage.load.getOrElse(
              ScastieState.default(props.isEmbedded)
            )

          if (!props.isEmbedded) {
            loadedState
          } else {
            loadedState.setCleanInputs.clearOutputs
          }
        }

        props.targetType match {
          case Some(targetType) => {
            val state0 =
              state.setTarget(targetType.defaultScalaTarget)

            if (targetType == ScalaTargetType.Dotty) {
              state0.setCode(ScalaTarget.Dotty.defaultCode)
            } else {
              state0
            }
          }
          case _ => state
        }
      }
      .backend(new ScastieBackend(scastieId, serverUrl, _))
      .renderPS(render)
      .componentWillMount { current =>
        current.backend.start(current.props) >>
          setTitle(current.state, current.props) >>
          current.backend.connectStatus >>
          current.backend.closeNewSnippetModal >>
          current.backend.closeResetModal
      }
      .componentWillUnmount { current =>
        current.backend.disconnectStatus >>
          current.backend.unsubscribeGlobal

      }
      .componentDidUpdate { scope =>
        val state = scope.prevState
        val scalaJsRunId = "scastie-scalajs-playground-run"

        def createScript(id: String): HTMLScriptElement = {
          val newScript = dom.document
            .createElement("script")
            .asInstanceOf[HTMLScriptElement]
          newScript.id = id
          dom.document.body.appendChild(newScript)
          newScript
        }

        def removeIfExist(id: String): Unit = {
          Option(dom.document.getElementById(id))
            .foreach(element => element.parentNode.removeChild(element))
        }

        def runScalaJs(): Unit = {
          removeIfExist(scalaJsRunId)
          val scalaJsRunScriptElement = createScript(scalaJsRunId)
          println("== Running Scala.js ==")

          val scalaJsScript =
            s"""|try {
                |  var main = new ScastiePlaygroundMain();
                |  scastie.ClientMain.signal(
                |    main.result,
                |    main.attachedElements,
                |    "$scastieId"
                |  );
                |} catch (e) {
                |  scastie.ClientMain.error(
                |    e,
                |    "$scastieId"
                |  );
                |}""".stripMargin

          scalaJsRunScriptElement.innerHTML = scalaJsScript
        }

        val executeScalaJs =
          Callback.when(
            state.loadScalaJsScript &&
              !state.isScalaJsScriptLoaded &&
              state.snippetIdIsScalaJS &&
              state.snippetId.nonEmpty &&
              !state.isRunning
          ) {

            val setLoaded =
              scope.modState(
                _.setLoadScalaJsScript(false).scalaJsScriptLoaded
                  .setRunning(true)
              )

            val loadJs =
              Callback {
                val scalaJsId = "scastie-scalajs-playground-target"
                println("== Loading Scala.js ==")

                removeIfExist(scalaJsId)
                val scalaJsScriptElement = createScript(scalaJsId)
                scalaJsScriptElement.onload = { (e: dom.Event) =>
                  runScalaJs()
                }
                if (state.snippetId.nonEmpty) {
                  timers.setTimeout(500) {

                    val apiBase = scope.currentProps.serverUrl.getOrElse("")

                    val snippetUrl =
                      apiBase + state.snippetId.get.scalaJsUrl(
                        ScalaTarget.Js.targetFilename
                      )

                    scalaJsScriptElement.src = snippetUrl
                  }
                  ()
                } else {
                  println("empty snippetId")
                }
              }

            setLoaded >> loadJs
          }

        val reRunScalaJs =
          Callback.when(state.isReRunningScalaJs)(
            Callback(runScalaJs()) >> scope
              .modState(_.setIsReRunningScalaJs(false))
          )

        setTitle(state, scope.currentProps) >> executeScalaJs >> reRunScalaJs
      }
      .componentWillReceiveProps { scope =>
        val next = scope.nextProps.snippetId
        val current = scope.currentProps.snippetId
        val state = scope.state
        val backend = scope.backend

        val loadSnippet: CallbackOption[Unit] =
          for {
            snippetId <- CallbackOption.liftOption(next)
            _ <- CallbackOption.require(next != current)
            _ <- CallbackOption.liftCallback(
              backend.loadSnippet(snippetId) >> backend.setView(View.Editor)
            )
          } yield ()

        setTitle(state, scope.nextProps) >> loadSnippet.toCallback
      }
      .configure(Reusability.shouldComponentUpdate)
      .build
}
