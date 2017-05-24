package com.olegych.scastie
package client
package components

import api._
import japgolly.scalajs.react._, vdom.all._, extra.router._
import japgolly.scalajs.react.component.builder.Lifecycle.RenderScope

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLScriptElement

import scalajs.js.timers

final case class Scastie(router: Option[RouterCtl[Page]],
                         snippetId: Option[SnippetId],
                         oldSnippetId: Option[Int],
                         embedded: Option[EmbededOptions],
                         targetType: Option[ScalaTargetType]) {

  @inline def render = Scastie.component(this)

  def isEmbedded: Boolean = embedded.isDefined
}

object Scastie {
  def default(router: RouterCtl[Page]): Scastie =
    Scastie(
      router = Some(router),
      snippetId = None,
      oldSnippetId = None,
      embedded = None,
      targetType = None
    )

  private def setTitle(state: ScastieState) =
    if (state.inputsHasChanged) {
      Callback(dom.document.title = "Scastie (*)")
    } else {
      Callback(dom.document.title = "Scastie")
    }

  private def render(
      scope: RenderScope[Scastie, ScastieState, ScastieBackend],
      props: Scastie,
      state: ScastieState
  ): VdomElement = {
    val theme =
      if (state.isDarkTheme) "dark"
      else "light"

    val embeddedClass =
      (cls := "embedded").when(props.isEmbedded)

    val forceDesktopClass =
      (cls := "force-desktop").when(state.isDesktopForced)

    div(cls := s"app $theme", embeddedClass, forceDesktopClass)(
      SideBar(
        isDarkTheme = state.isDarkTheme,
        toggleTheme = scope.backend.toggleTheme,
        view = scope.backend.viewSnapshot(state.view),
        openHelpModal = scope.backend.openHelpModal
      ).render.unless(props.isEmbedded),
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

  private val component =
    ScalaComponent
      .builder[Scastie]("Scastie")
      .initialStateFromProps { props =>
        val state = LocalStorage.load.getOrElse(ScastieState.default)

        props.targetType match {
          case Some(targetType) =>
            state.setTarget(targetType.defaultScalaTarget)
          case _ => state
        }
      }
      .backend(new ScastieBackend(_))
      .renderPS(render _)
      .componentWillMount { current =>
        current.backend.start(current.props) >> setTitle(current.state)
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
          scalaJsRunScriptElement.innerHTML =
            """|try {
               |  var main = new Main();
               |  com.olegych.scastie.client.ClientMain().signal(
               |    main.result,
               |    main.attachedElements
               |  );
               |} catch (e) {
               |  com.olegych.scastie.client.ClientMain().error(e);
               |}""".stripMargin
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
                    scalaJsScriptElement.src =
                      state.snippetId.get.url(ScalaTarget.Js.targetFilename)
                  }
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

        setTitle(state) >> executeScalaJs >> reRunScalaJs
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

        setTitle(state) >> loadSnippet.get.void
      }
      .build
}
