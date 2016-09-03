package client

import org.scalajs.dom
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router._

import api._
import autowire._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

sealed trait Page
case object Home extends Page

object Client extends JSApp {
  val routerConfig = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._

    ( trimSlashes
        | staticRoute(root, Home) ~> render(App())
    ) 
      .notFound(redirectToPage(Home)(Redirect.Replace))
      .renderWith(layout)
  }

  def layout(c: RouterCtl[Page], r: Resolution[Page]) =
    r.render()
    
  @JSExport
  override def main(): Unit = {
    api.Client[Api].run("test").call().onSuccess{ case response ⇒
      dom.console.log(response)
    }

    ReactDOM.render(
      Router(BaseUrl.fromWindowOrigin_/, routerConfig.logToConsole)(),
      dom.document.body
    )
    ()
  }
}