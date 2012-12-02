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
import com.olegych.scastie.PastesActor.{GetPaste, Paste, AddPaste}
import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import concurrent.duration._
import play.api.libs.json.JsValue
import controllers.Progress.{MonitorChannel, MonitorProgress}


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
      "paste" -> text
    )
  )

  def add = Action { implicit request =>
    Async {
      val paste = pasteForm.bindFromRequest().apply("paste").value.get
      (renderer ? AddPaste(paste)).mapTo[Paste].map { paste =>
        Redirect(routes.Pastes.show(paste.id))
      }
    }
  }

  def show(id: Long) = Action { implicit request =>
    Async {
      (renderer ? GetPaste(id)).mapTo[Paste].map { paste =>
        val content = paste.content.getOrElse("")
        val output = paste.output.getOrElse("")
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
