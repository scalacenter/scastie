package com.olegych.scastie.client.components

import com.olegych.scastie.api._
import com.olegych.scastie.client._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.builder.Lifecycle.RenderScope
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.all._
import org.scalajs.dom
import org.scalajs.dom.HTMLScriptElement

import java.util.UUID

final case class Scastie(
    router: Option[RouterCtl[Page]],
    private val scastieId: UUID,
    private val snippetId: Option[SnippetId],
    private val oldSnippetId: Option[Int],
    private val embedded: Option[EmbeddedOptions],
    private val targetType: Option[ScalaTargetType],
    private val tryLibrary: Option[(ScalaDependency, Project)],
    private val code: Option[String],
    private val inputs: Option[Inputs],
) {

  @inline def render = Scastie.component(serverUrl, scastieId)(this)

  def serverUrl: Option[String] = embedded.map(_.serverUrl)
  def isEmbedded: Boolean = embedded.isDefined
  //todo not sure how is it different from regular snippet id
  def embeddedSnippetId: Option[SnippetId] = embedded.flatMap(_.snippetId)
}

object Scastie {
  implicit val scastieReuse: Reusability[Scastie] =
    Reusability.derive[Scastie]

  def default(router: RouterCtl[Page]): Scastie =
    Scastie(
      scastieId = UUID.randomUUID(),
      router = Some(router),
      snippetId = None,
      oldSnippetId = None,
      embedded = None,
      targetType = None,
      tryLibrary = None,
      code = None,
      inputs = None,
    )

  private def setTitle(state: ScastieState, props: Scastie) = {
    def scastieCode = if (state.inputs.code.isEmpty) "Scastie" else state.inputs.code + " - Scastie"
    if (!props.isEmbedded) {
      if (state.inputsHasChanged) {
        Callback(dom.document.title = "* " + scastieCode)
      } else {
        Callback(dom.document.title = scastieCode)
      }
    } else {
      Callback(())
    }
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
        toggleTheme = scope.backend.toggleTheme,
        view = scope.backend.viewSnapshot(state.view),
        openHelpModal = scope.backend.openHelpModal,
        openPrivacyPolicyModal = scope.backend.openPrivacyPolicyModal
      ).render.unless(props.isEmbedded || state.isPresentationMode),
      MainPanel(
        state,
        scope.backend,
        props
      ).render,
      HelpModal(
        isDarkTheme = state.isDarkTheme,
        isClosed = state.modalState.isHelpModalClosed,
        close = scope.backend.closeHelpModal
      ).render,
      LoginModal(
        isDarkTheme = state.isDarkTheme,
        isClosed = state.modalState.isLoginModalClosed,
        close = scope.backend.closeLoginModal,
        openPrivacyPolicyModal = scope.backend.openPrivacyPolicyModal,
      ).render,
      PrivacyPolicyPrompt(
        isDarkTheme = state.isDarkTheme,
        isClosed = state.modalState.isPrivacyPolicyPromptClosed,
        acceptPrivacyPolicy = scope.backend.acceptPolicy,
        refusePrivacyPolicy = scope.backend.refusePrivacyPolicy,
        openPrivacyPolicyModal = scope.backend.openPrivacyPolicyModal,
      ).render,
      PrivacyPolicyModal(
        isDarkTheme = state.isDarkTheme,
        isClosed = state.modalState.isPrivacyPolicyModalClosed,
        close = scope.backend.closePrivacyPolicyModal
      ).render,
    )
  }

  private def start(props: Scastie, backend: ScastieBackend): Callback = {
    val initialState =
      props.embedded match {
        case None => {
          props.snippetId match {
            case Some(snippetId) =>
              backend.loadSnippet(snippetId)

            case None =>
              props.oldSnippetId match {
                case Some(id) =>
                  backend.loadOldSnippet(id)

                case None =>
                  Callback.traverseOption(LocalStorage.load) { state =>
                    backend.scope.modState { _ =>
                      state
                        .setRunning(false)
                        .setCleanInputs
                        .resetScalajs
                    }
                  }
              }
          }
        }
        case Some(embededOptions) => {
          val setInputs =
            (embededOptions.snippetId, embededOptions.inputs) match {
              case (Some(snippetId), _) =>
                backend.loadSnippet(snippetId)

              case (_, Some(inputs)) =>
                backend.scope.modState(_.setInputs(inputs))

              case _ => Callback.empty
            }

          val setTheme =
            embededOptions.theme match {
              case Some("dark")  => backend.scope.modState(_.setTheme(dark = true))
              case Some("light") => backend.scope.modState(_.setTheme(dark = false))
              case _             => Callback(())
            }

          setInputs >> setTheme
        }
      }

    initialState >> backend.loadUser
  }

  private def executeScalaJs(scastieId: UUID, state: ScastieState): CallbackTo[Unit] = {
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
      Option(dom.document.getElementById(id)).foreach { element =>
        element.parentNode.removeChild(element)
      }
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

    Callback.when(state.snippetId.nonEmpty && !state.isRunning) {
      Callback {
        val scalaJsId = "scastie-scalajs-playground-target"
        removeIfExist(scalaJsId)
        state.snippetState.scalaJsContent.foreach { content =>
          println("== Loading Scala.js! ==")
          val scalaJsScriptElement = createScript(scalaJsId)
          val fixedContent = content.replace("let ScastiePlaygroundMain;", "var ScastiePlaygroundMain;")
          val scriptTextNode = dom.document.createTextNode(fixedContent)
          scalaJsScriptElement.appendChild(scriptTextNode)
          runScalaJs()
        }
      }
    }
  }

  private def component(serverUrl: Option[String], scastieId: UUID) =
    ScalaComponent
      .builder[Scastie]("Scastie")
      .initialStateFromProps { props =>
        val state = {
          val loadedState = ScastieState.default(props.isEmbedded).copy(inputs = Inputs.default.copy(code = ""))
          if (!props.isEmbedded) {
            loadedState
          } else {
            loadedState.setCleanInputs.clearOutputs
          }
        }

        val state1 =
          props.targetType match {
            case Some(targetType) => {
              val state0 =
                state.setTarget(targetType.defaultScalaTarget)

              if (targetType == ScalaTargetType.Scala3) {
                state0.setCode(ScalaTarget.Scala3.defaultCode)
              } else {
                state0
              }
            }
            case _ => state
          }

        val state2 = props.tryLibrary match {
          case Some(dependency) =>
            state1
              .setTarget(dependency._1.target)
              .addScalaDependency(dependency._1, dependency._2)
          case _ => state1
        }

        val state3 = props.code match {
          case Some(code) => state2.setCode(code)
          case _          => state2
        }

        props.inputs match {
          case Some(inputs) => state3.setInputs(inputs)
          case _            => state3
        }
      }
      .backend(ScastieBackend(scastieId, serverUrl, _))
      .renderPS(render)
      .componentWillMount { current =>
        start(current.props, current.backend) >>
          setTitle(current.state, current.props) >>
          current.backend.closeNewSnippetModal >>
          current.backend.closeResetModal >>
          current.backend.connectStatus.when_(!current.props.isEmbedded)
      }
      .componentWillUnmount { current =>
        current.backend.disconnectStatus.when_(!current.props.isEmbedded) >>
          current.backend.unsubscribeGlobal
      }
      .componentDidUpdate { scope =>
        setTitle(scope.prevState, scope.currentProps) >>
          scope.modState(_.scalaJsScriptLoaded) >>
          executeScalaJs(scastieId, scope.currentState)
      }
      .componentWillReceiveProps { scope =>
        val next = scope.nextProps.snippetId
        val current = scope.currentProps.snippetId
        val state = scope.state
        val backend = scope.backend

        val loadSnippet: CallbackOption[Unit] =
          for {
            snippetId <- CallbackOption.option(next)
            _ <- CallbackOption.require(next != current)
            _ <- backend.loadSnippet(snippetId).toCBO >> backend.setView(View.Editor)
          } yield ()

        setTitle(state, scope.nextProps) >> loadSnippet.toCallback
      }
      .configure(Reusability.shouldComponentUpdate)
      .build
}
