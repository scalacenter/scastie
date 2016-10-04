package client

import japgolly.scalajs.react._, vdom.all._, extra.router.RouterCtl

import api._
import autowire._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import org.scalajs.dom.{WebSocket, MessageEvent, Event, CloseEvent, ErrorEvent, window}
import scala.util.{Success, Failure}

import upickle.default.{read => uread}

object App {
  case class State(
    code: String,
    websocket: Option[WebSocket] = None,
    output: Vector[String] = Vector(),
    dark: Boolean = true,
    compilationInfos: Set[api.Problem] = Set(),
    instrumentations: Set[api.Instrumentation] = Set(),
    sideBarClosed: Boolean = true) {

    def toogleTheme             = copy(dark = !dark)
    def toogleSidebar           = copy(sideBarClosed = !sideBarClosed)
    def log(line: String)       = copy(output = output :+ line)
    def log(lines: Seq[String]) = copy(output = output ++ lines)
  }

  class Backend(scope: BackendScope[_, State]) {
    def codeChange(newCode: String) = scope.modState(_.copy(code = newCode))

    private def connect(id: Long) = CallbackTo[WebSocket]{
      val direct = scope.accessDirect

      def onopen(e: Event): Unit = direct.modState(_.log("Connected."))
      def onmessage(e: MessageEvent): Unit = {
        val progress = uread[PasteProgress](e.data.toString)
        direct.modState( s =>
          s.log(progress.output).copy(
            compilationInfos = s.compilationInfos ++ progress.compilationInfos.toSet,
            instrumentations = s.instrumentations ++ progress.instrumentations.toSet
          )
        )
      }
      def onerror(e: ErrorEvent): Unit = direct.modState(_.log(s"Error: ${e.message}"))
      def onclose(e: CloseEvent): Unit = direct.modState(_.copy(websocket = None).log(s"Closed: ${e.reason}"))

      val protocol = if(window.location.protocol == "https:") "wss" else "ws"
      val uri = s"$protocol://${window.location.host}/progress/$id"
      val socket = new WebSocket(uri)

      socket.onopen = onopen _
      socket.onclose = onclose _
      socket.onmessage = onmessage _
      socket.onerror = onerror _
      socket
    }

    def run() = {
      scope.state.map(s =>
        api.Client[Api].run(s.code).call().onSuccess{ case id =>
          val direct = scope.accessDirect
          connect(id).attemptTry.map {
            case Success(ws)    =>
              direct.modState(_.log("Connecting...").copy(
                websocket = Some(ws),
                output = Vector(),
                compilationInfos = Set(),
                instrumentations = Set())
              )
            case Failure(error) => direct.modState(_.log(error.toString).copy(compilationInfos = Set()))
          }.runNow()
        }
      )
    }
    def runE(e: ReactEventI) = run()
    def start(props: (RouterCtl[Page], Option[Snippet])): Callback = {
      val (router, snippet) = props

      snippet match {
        case Some(Snippet(id)) => Callback.future(api.Client[Api].fetch(id).call().map(codeChange))
        case None              => Callback(())
      }
    }

    def toogleTheme() = scope.modState(_.toogleTheme)
  }

  val SideBar = ReactComponentB[(State, Backend)]("SideBar")
    .render_P { case (state, backend) =>
      div(
        button(onClick ==> backend.runE)("run")
      )
    }
    .build

  val defaultCode =
    """|/***
       |
       |scalaVersion := "2.11.8"
       |// scalaVersion := "2.10.6"
       |
       |*/
       |
       |object Main {
       |  def main(args: Array[String]): Unit = {
       |    println(util.Properties.versionString)
       |  }
       |}""".stripMargin

  val component = ReactComponentB[(RouterCtl[Page], Option[Snippet])]("App")
    .initialState(State(code = defaultCode))
    .backend(new Backend(_))
    .renderPS{ case (scope, (router, snippet), state) => {
      val sideStyle =
        if(state.sideBarClosed) "sidebar-closed"
        else "sidebar-open"

      val hideOutput = 
        if(state.output.isEmpty) display.none
        else display.block

      div(`class` := "app")(
        div(`class` := s"editor $sideStyle")(
          Editor(state, scope.backend),
          ul(`class` := "output", hideOutput)(
            state.output.map(o => li(o))
          )
        ),
        div(`class` := s"sidebar $sideStyle")(SideBar((state, scope.backend)))
      )
    }}
    .componentDidMount(s => s.backend.start(s.props))
    .build

  def apply(router: RouterCtl[Page], snippet: Option[Snippet]) = component((router, snippet))
}
