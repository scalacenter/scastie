package controllers

import api._

import com.olegych.scastie._
import com.olegych.scastie.PastesActor._
import controllers.Progress.{MonitorChannel, MonitorProgress}

import autowire.Core.Request

import play.api.Play
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsValue

import akka.util.Timeout
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import com.typesafe.config.ConfigFactory

import upickle.default.{read => uread}
import java.nio.ByteBuffer
import scala.concurrent.Future

import scala.concurrent.duration._


class ApiImpl(renderer: ActorRef)(implicit timeout: Timeout) extends Api {
  def run(code: String, sbtConfig: String, scalaTargetType: ScalaTargetType): Future[Long] = {
    (renderer ? AddPaste(code, sbtConfig, scalaTargetType, "-no-uid-")).mapTo[Paste].map(_.id)
  }
  def fetch(id: Long): Future[String] = {
    (renderer ? GetPaste(id)).mapTo[Paste].map(_.content.getOrElse(""))
  }
}

object Application extends Controller {

  import play.api.Play.current
  implicit val timeout = Timeout(100.seconds)
  val system = {
    val classloader = Play.application.classloader
    val config = ConfigFactory.load(classloader, Play.configuration.getString("actors.conf").get)
    ActorSystem("actors", config, classloader)
  }

  val progressActor = system.actorOf(Props[Progress])
  val container = PastesContainer(new java.io.File(Play.configuration.getString("pastes.data.dir").get))
  val renderer = system.actorOf(Props(new PastesActor(container, progressActor)), "pastes")

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def index2(rest: String) = Action { implicit request =>
    Ok(views.html.index())
  }

  private val api = new ApiImpl(renderer)

  def progress(id: Long) = WebSocket.tryAccept[String] { request =>
    (progressActor ? MonitorProgress(id)).mapTo[MonitorChannel].map(m => Right(m.value))
  }

  def autowireApi(path: String) = Action.async { implicit request =>
    val text = request.body.asText.getOrElse("")
    val autowireRequest = Request(path.split("/"), uread[Map[String, String]](text))
    AutowireServer.route[Api](api)(autowireRequest).map(buffer => Ok(buffer))
  }
}
