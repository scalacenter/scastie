package controllers

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import akka.actor.Props
import com.olegych.scastie.{PastesContainer, RendererActor}
import akka.pattern.ask
import akka.util.duration._
import play.api.libs.concurrent._
import akka.util.Timeout
import play.api.Play
import java.io.File


object Pastes extends Controller {

  import play.api.Play.current
  import concurrent.ExecutionContext.Implicits.global

  val pastesDir = new File(Play.configuration.getString("pastes.data.dir").getOrElse("./target/pastes/"))
  val renderer = Akka.system.actorOf(Props(new RendererActor(PastesContainer(pastesDir))))

  implicit val timeout = Timeout(100 second)

  val pasteForm = Form(
    single(
      "paste" -> text
    )
  )

  def add = Action { implicit request =>
    Async {
      val paste = pasteForm.bindFromRequest().apply("paste").value.get
      (renderer ? paste).mapTo[String].asPromise.map { response =>
        Redirect(routes.Pastes.show("111")).flashing("paste" -> response)
      }
    }
  }

  def show(id: String) = Action { implicit request =>
    Ok(views.html.index(id + " " + request.flash.get("paste").getOrElse("") + " Pasted!"))
  }
}
