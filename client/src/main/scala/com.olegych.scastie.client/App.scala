package com.olegych.scastie
package client

import api._
import japgolly.scalajs.react._
import vdom.all._
import org.scalajs.dom
import org.scalajs.dom.UIEvent
import org.scalajs.dom.raw.HTMLScriptElement

object App {
  val component =
    ReactComponentB[AppProps]("App")
      .initialState(LocalStorage.load.getOrElse(AppState.default))
      .backend(new AppBackend(_))
      .renderPS {
        case (scope, props, state) => {
          val theme =
            if (state.isDarkTheme) "dark"
            else "light"

          val sideBar =
            if (!props.isEmbedded)
              TagMod(SideBar(state, scope.backend, props.snippetId))
            else EmptyTag

          val appClass =
            if (!props.isEmbedded) "app"
            else "app embedded"

          def onresize(e: UIEvent): Unit =
              scope.backend.setWindowHasResized().runNow

          dom.window.onresize = onresize _

          def appStyle: TagMod = Seq(
            height := dom.window.innerHeight,
            width := dom.window.innerWidth)

          div(`class` := s"$appClass $theme", appStyle)(
            sideBar,
            MainPanel(state, scope.backend, props)
          )
        }
      }
      .componentWillMount(s => s.backend.start(s.props))
      .componentDidUpdate { v =>
        val state = v.prevState
        val scope = v.$

        val direct = scope.accessDirect

        val setTitle =
          if (state.inputsHasChanged) {
            Callback(dom.document.title = "Scastie (*)")
          } else {
            Callback(dom.document.title = "Scastie")
          }

        val executeScalaJs =
          if (direct.state.loadScalaJsScript &&
              !direct.state.isScalaJsScriptLoaded &&
              state.snippetIdIsScalaJS &&
              state.snippetId.nonEmpty &&
              !state.running) {

            direct.modState(
              _.setLoadScalaJsScript(false) 
               .scalaJsScriptLoaded
            )

            Callback {
              val scalaJsId = "scastie-scalajs-playground-target"
              val scalaJsRunId = "scastie-scalajs-playground-run"

              def scalaJsUrl(snippetId: SnippetId): String = {
                val middle =
                  snippetId match {
                    case SnippetId(uuid, None) => uuid
                    case SnippetId(uuid,
                                   Some(SnippetUserPart(login, update))) =>
                      s"$login/$uuid/${update.getOrElse(0)}"
                  }

                s"/${Shared.scalaJsHttpPathPrefix}/$middle/${ScalaTarget.Js.targetFilename}"
              }

              def createScript(id: String): HTMLScriptElement = {
                val newScript = dom.document
                  .createElement("script")
                  .asInstanceOf[HTMLScriptElement]
                newScript.id = id
                dom.document.body.appendChild(newScript)
                newScript
              }

              def removeIfExist(id: String): Unit = {
                Option(dom.document.getElementById(id)).foreach(element =>
                  element.parentNode.removeChild(element))
              }

              println("== Loading Scala.js ==")

              removeIfExist(scalaJsId)
              val scalaJsScriptElement = createScript(scalaJsId)
              scalaJsScriptElement.onload = { (e: dom.Event) =>
                removeIfExist(scalaJsRunId)
                val scalaJsRunScriptElement = createScript(scalaJsRunId)
                println("== Running Scala.js ==")
                scalaJsRunScriptElement.innerHTML =
                  """|try {
                     |  var main = Main()
                     |  com.olegych.scastie.client.ClientMain().signal(
                     |    main.result,
                     |    main.attachedElements
                     |  )
                     |} catch (e) {
                     | console.log("== Caught JS Error ==")
                     | console.log(e)
                     |}""".stripMargin
              }
              if (state.snippetId.nonEmpty) {
                scalaJsScriptElement.src = scalaJsUrl(state.snippetId.get)
              } else {
                println("empty snippetId")
              }
            }
          } else Callback(())

        setTitle >> executeScalaJs
      }
      .componentWillReceiveProps { v =>
        val next = v.nextProps.snippetId
        val current = v.currentProps.snippetId
        val state = v.$.state
        val backend = v.$.backend

        if (next != current) {
          next match {
            case Some(snippetId) =>
              backend.loadSnippet(snippetId) >>
                backend.setView(View.Editor)
            case _ => Callback(())
          }

        } else Callback(())
      }
      .build

  def apply(props: AppProps) = component(props)
}
