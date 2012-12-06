package controllers

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import akka.actor.Props
import com.olegych.scastie.{PastesActor, PastesContainer}
import akka.pattern.ask
import play.api.Play
import java.io.File
import play.api.templates.Html
import com.olegych.scastie.PastesActor.{DeletePaste, GetPaste, Paste, AddPaste}
import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import concurrent.duration._
import play.api.libs.json.JsValue
import controllers.Progress.{MonitorChannel, MonitorProgress}
import play.api.i18n.Messages


object Pastes extends Controller {

  import play.api.Play.current
  import concurrent.ExecutionContext.Implicits.global

  val pastesDir = new File(Play.configuration.getString("pastes.data.dir").get)
  val system = {
    val classloader = Play.application.classloader
    akka.actor.ActorSystem("actors",
      ConfigFactory.load(classloader, Play.configuration.getString("actors.conf").get), classloader)
  }

  val progressActor = system.actorOf(Props[Progress])
  val renderer = system.actorOf(Props(new PastesActor(PastesContainer(pastesDir), progressActor)), "pastes")

  implicit val timeout = Timeout(100 seconds)

  val pasteForm = Form(
    single(
      "paste" -> text(maxLength = 10000)
    )
  )

  def add = Action { implicit request =>
    val form = pasteForm.bindFromRequest()
    createPaste(form, Application.uid)
  }

  def createPaste(form: Form[String], uid: String): Result = {
    val paste = form("paste").value.get
    if (form.hasErrors) {
      Redirect(routes.Application.index())
          .flashing("error" -> form.errors.map(_.message).mkString, "paste" -> paste)
    } else {
      Async {
        (renderer ? AddPaste(paste, uid)).mapTo[Paste].map { paste =>
          Redirect(routes.Pastes.show(paste.id))
        }
      }
    }
  }

  def edit = Action { implicit request =>
    val form = pasteForm.bindFromRequest()
    Redirect(routes.Application.index()).flashing("paste" -> form("paste").value.get)
  }

  def delete(id: Long) = Action { implicit request =>
    Async {
      (renderer ? DeletePaste(id, Application.uid)).mapTo[Paste].map { paste =>
        val result = Redirect(routes.Pastes.show(id))
        paste.uid.fold[PlainResult](ifEmpty = result)(
          _ => result.flashing("error" -> Messages("invalid.user")))
      }
    }
  }

  def show(id: Long) = Action { implicit request =>
    Async {
      (renderer ? GetPaste(id)).mapTo[Paste].map { paste =>
        val content = paste.content.getOrElse("")
        val output = request.flash.get("error").map(_ + "\n").getOrElse("") + paste.output.getOrElse("")
        val typedContent = if (content.matches("(?mis)\\s*<pre>.*")) Left(Html(content)) else Right(content)
        val ref = """\[(?:error|warn)\].*test.scala:(\d+)""".r
        val highlights = ref.findAllIn(output).matchData.map(_.group(1).toInt).toSeq
        Ok(views.html.show(typedContent, output, highlights, id))
      }
    }
  }

  def progress(id: Long) = WebSocket.async[JsValue] { request =>
    (progressActor ? MonitorProgress(id)).mapTo[MonitorChannel].map(_.value)
  }

}
