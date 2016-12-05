package controllers

import com.olegych.scastie._
import web._

import Progress._
import api._

import autowire.Core.Request
import upickle.default.{read => uread}

import play.api.Play
import play.api.mvc._
import play.api.libs.json.JsValue

import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask

import java.nio.ByteBuffer
import scala.concurrent.Future

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class ApiImpl(pasteActor: ActorRef)(implicit timeout: Timeout, executionContext: ExecutionContext) extends Api {
  def run(code: String,
          sbtConfig: String,
          scalaTargetType: ScalaTargetType): Future[Long] = {
    (pasteActor ? AddPaste(code, sbtConfig, scalaTargetType)).mapTo[Long]
  }
  def fetch(id: Long): Future[Option[String]] = {
    (pasteActor ? GetPaste(id)).mapTo[Option[String]]
  }
}

object Application extends Controller {

  import play.api.Play.current
  implicit val timeout = Timeout(100.seconds)

  val system = {
    val classloader = Play.application.classloader
    val config = ConfigFactory
      .load(classloader, Play.configuration.getString("actors.conf").get)
    ActorSystem("actors", config, classloader)
  }
  import system.dispatcher

  val progressActor = system.actorOf(Props[ProgressActor], name = "ProgressActor")
  val pasteActor = system.actorOf(Props(new PasteActor(progressActor)), name = "PasteActor")

  def tmp(file: String) = Action { implicit request =>
    Ok.sendFile(new java.io.File("/tmp/" + file))
  }

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def index2(rest: String) = Action { implicit request =>
    Ok(views.html.index())
  }

  private val api = new ApiImpl(pasteActor)

  def progress(id: Long) = WebSocket.tryAccept[String] { request =>
    (progressActor ? MonitorProgress(id))
      .mapTo[MonitorChannel]
      .map(m => Right(m.value))
  }

  def autowireApi(path: String) = Action.async { implicit request =>
    val text = request.body.asText.getOrElse("")
    val autowireRequest =
      Request(path.split("/"), uread[Map[String, String]](text))
    AutowireServer.route[Api](api)(autowireRequest).map(buffer => Ok(buffer))
  }
}
