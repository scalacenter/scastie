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
import scalaz._
import Scalaz._


object Pastes extends Controller {

  import play.api.Play.current
  import scala.concurrent.ExecutionContext.Implicits.global

  val pastesDir = new File(Play.configuration.getString("pastes.data.dir").get)
  val system = {
    val classloader = Play.application.classloader
    akka.actor.ActorSystem("actors",
      ConfigFactory.load(classloader, Play.configuration.getString("actors.conf").get), classloader)
  }

  val progressActor = system.actorOf(Props[Progress])
  val container = PastesContainer(pastesDir)
  val renderer = system.actorOf(Props(new PastesActor(container, progressActor)), "pastes")

  implicit val timeout = Timeout(100 seconds)

  case class NewPaste(paste: String, id: Option[Long])

  val pasteForm = Form(
    mapping(
      "paste" -> text(maxLength = 10000),
      "id" -> optional(longNumber)
    )(NewPaste.apply)(NewPaste.unapply)
  )

  def add = Action { implicit request =>
    val form = pasteForm.bindFromRequest()
    createPaste(form, Application.uid)
  }

  def createPaste(form: Form[NewPaste], uid: String): Result = {
    val paste = form("paste").value.get
    if (form.hasErrors) {
      Redirect(routes.Application.index())
          .flashing("error" -> form.errors.map(_.message).mkString, "paste" -> paste)
    } else {
      scala.concurrent.future()
      Async {
        (renderer ? AddPaste(paste, uid)).mapTo[Paste].map { paste =>
          Redirect(routes.Pastes.show(paste.id))
        }
      }
    }
  }

  def edit = Action { implicit request =>
    val form = pasteForm.bindFromRequest().get
    val pasteById = form.id.map(id => container.paste(id).pasteFile.read.getOrElse(""))
    Redirect(routes.Application.index()).flashing("paste" -> pasteById.getOrElse(form.paste))
  }

  def delete(id: Long) = Action.async { implicit request =>
      (renderer ? DeletePaste(id, Application.uid)).mapTo[Paste].map { paste =>
        val result = Redirect(routes.Pastes.show(id))
        paste.uid.cata(_ => result.flashing("error" -> Messages("invalid.user")), result)
      }
  }

  def show(id: Long) = Action.async { implicit request =>
      (renderer ? GetPaste(id)).mapTo[Paste].map { paste =>
        val content = paste.content.getOrElse("")
        val output = request.flash.get("error").map(_ + "\n").getOrElse("") + paste.output.getOrElse("")
        val typedContent = if (content.matches("(?mis)\\s*<pre>.*")) Left(Html(content)) else Right(content)
        val ref = """\[(?:error|warn)\].*test.scala:(\d+)""".r
        val highlights = ref.findAllIn(output).matchData.map(_.group(1).toInt).toSeq
        Ok(views.html.show(typedContent, output, highlights, id))
    }
  }

  def progress(id: Long) = WebSocket.async[JsValue] { request =>
    (progressActor ? MonitorProgress(id)).mapTo[MonitorChannel].map(_.value)
  }

}
