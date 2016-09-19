package client

import japgolly.scalajs.react._, vdom.all._

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
    dark: Boolean = false,
    compilationInfos: Set[CompilationInfo] = Set(),
    sideBarClosed: Boolean = false) {

    def toogleTheme             = copy(dark = !dark)
    def toogleSidebar           = copy(sideBarClosed = !sideBarClosed)
    def log(line: String)       = copy(output = output :+ line)
    def log(lines: Seq[String]) = copy(output = output ++ lines)
  }

  class Backend(scope: BackendScope[_, State]) {
    def codeChange(newCode: String) = scope.modState(_.copy(code = newCode))

    private def connect(id: Long) = CallbackTo[WebSocket]{
      val direct = scope.accessDirect

      def onopen(e: Event): Unit           = direct.modState(_.log("Connected."))
      def onmessage(e: MessageEvent): Unit = {
        val progress = uread[PasteProgress](e.data.toString)
        direct.modState(_.log(progress.output))
      }
      def onerror(e: ErrorEvent): Unit     = direct.modState(_.log(s"Error: ${e.message}"))
      def onclose(e: CloseEvent): Unit     = direct.modState(_.copy(websocket = None).log(s"Closed: ${e.reason}"))

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
                output = Vector())
              )
            case Failure(error) => direct.modState(_.log(error.toString))
          }.runNow()
        }
      )
    }
    def runE(e: ReactEventI) = run()

    // def toogleTheme(e: ReactEventI)   = toogleTheme2()
    def toogleTheme()                = scope.modState(_.toogleTheme)
    // def toogleSidebar(e: ReactEventI) = scope.modState(_.toogleSidebar)
    // def templateOne(e: ReactEventI)   = scope.modState(_.copy(code = "code 1"))
    // def templateTwo(e: ReactEventI)   = scope.modState(_.copy(code = "code 2"))
    // def templateThree(e: ReactEventI) = scope.modState(_.copy(code = "code 3"))


    // def clearError(e: ReactEventI) = scope.modState(_.copy(compilationInfos = Set()))
    // def addError(e: ReactEventI)    = scope.modState(_.copy(compilationInfos = Set(e1, e2, e3)))
    // def addError2(e: ReactEventI)  = scope.modState(_.copy(compilationInfos = Set(e2)))
    // def addError3(e: ReactEventI)  = scope.modState(_.copy(compilationInfos = Set(e1, e3)))
  }

  // val e1 = CompilationInfo(severity = Error, position = Position(0, 10), message = "error: foo is baz")
  // val e2 = CompilationInfo(severity = Warning, position = Position(40, 50), message = "warning: ...")
  // val e3 = CompilationInfo(severity = Info, position = Position(60, 80), message = "info: ...")

  val SideBar = ReactComponentB[(State, Backend)]("SideBar")
    .render_P { case (state, backend) =>
      val label = if(state.dark) "light" else "dark"
      ul(
        // li(button(onClick ==> backend.toogleSidebar)("X")),
        li(button(onClick ==> backend.runE)("run")),
        // li(button(onClick ==> backend.toogleTheme)(label)),
        // li(button(onClick ==> backend.templateOne)("template 1")),
        // li(button(onClick ==> backend.templateTwo)("template 2")),
        // li(button(onClick ==> backend.templateThree)("template 3")),
        // li(button(onClick ==> backend.addError)("addError")),
        // li(button(onClick ==> backend.addError2)("addError2")),
        // li(button(onClick ==> backend.addError3)("addError3")),
        // li(button(onClick ==> backend.clearError)("clearError")),
        li(pre(state.code))
      )
    }
    .build


  val defaultCode = 
    """|/***
       |coursier.CoursierPlugin.projectSettings
       |scalaVersion := "2.11.8"
       |*/
       |object Example {
       |  def main(args: Array[String]): Unit = {
       |    println("Hello, world!")
       |  }
       |}""".stripMargin

  val defualtState = State(
    code = defaultCode
  )

  val component = ReactComponentB[Unit]("App")
    .initialState(State(code = defaultCode))
    .backend(new Backend(_))
    .renderPS((scope, _, state) => {
      val sideStyle = 
        if(state.sideBarClosed) "sidebar-closed"
        else "sidebar-open"

      div(`class` := "app")(
        div(`class` := s"editor $sideStyle")(
          Editor(state, scope.backend),
          ul(`class` := "output")(
            state.output.map(o => li(o))
          )  
        ),
        div(`class` := s"sidebar $sideStyle")(SideBar((state, scope.backend)))
      )
    })
    .build

  def apply() = component()
}