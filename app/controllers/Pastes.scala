package controllers

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import akka.actor.Props
import com.olegych.scastie.RendererActor
import akka.pattern.ask
import akka.util.duration._
import play.api.libs.concurrent._
import akka.util.Timeout


object Pastes extends Controller {

  import play.api.Play.current
  import concurrent.ExecutionContext.Implicits.global

  val renderer = Akka.system.actorOf(Props[RendererActor])
  implicit val timeout = Timeout(100 second)

  val pasteForm = Form(
    single(
      "paste" -> text
    )
  )

  def add = Action { implicit request =>
    Async {
      (renderer ? pasteForm.bindFromRequest().apply("paste").value).mapTo[String].asPromise.map { response =>
        Redirect(routes.Pastes.show("111")).flashing("paste" -> response)
      }
    }
  }

  def show(id: String) = Action { implicit request =>
    Ok(views.html.index(id + " " + request.flash.get("paste").getOrElse("") + " Pasted!"))
  }
}