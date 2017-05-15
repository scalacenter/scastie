package com.olegych.scastie
package client
package components

import api._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all.{`class` => clazz, _}

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLScriptElement

import scalajs.js.timers

object App {

  private def setTitle(state: AppState) =
    if (state.inputsHasChanged) {
      Callback(dom.document.title = "Scastie (*)")
    } else {
      Callback(dom.document.title = "Scastie")
    }

  private val component =
    ScalaComponent
      .builder[AppProps]("App")
      .initialStateFromProps{ props =>
        val state = LocalStorage.load.getOrElse(AppState.default)

        props.targetType match {
          case Some(targetType) => state.setTarget(targetType.defaultScalaTarget)
          case _ => state
        }
      }
      .backend(new AppBackend(_))
      .renderPS {
        case (scope, props, state) => {
          val theme =
            if (state.isDarkTheme) "dark"
            else "light"

          val embeddedClass =
            TagMod(clazz := "embedded").when(props.isEmbedded)
          val forceDesktopClass =
            TagMod(clazz := "force-desktop").when(state.isDesktopForced)

          div(clazz := s"app $theme", embeddedClass, forceDesktopClass)(
            SideBar(state, scope.backend).unless(props.isEmbedded),
            MainPanel(state, scope.backend, props),
            WelcomeModal(
              isClosed = state.modalState.isWelcomeModalClosed,
              close = scope.backend.closeWelcomeModal
            ),
            HelpModal(
              isClosed = state.modalState.isHelpModalClosed,
              close = scope.backend.closeHelpModal
            )
          )
        }
      }
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

  def apply(props: AppProps) = component(props)
}
