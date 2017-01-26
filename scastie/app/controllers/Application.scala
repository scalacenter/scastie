package controllers

import com.olegych.scastie._
import web._

import Progress._
import api._

import autowire.Core.Request
import upickle.default.{Reader, Writer, read => uread, write => uwrite}

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

object AutowireServer extends autowire.Server[String, Reader, Writer] {
  def read[R: Reader](p: String) = uread[R](p)
  def write[R: Writer](r: R) = uwrite(r)
}

class ApiImpl(pasteActor: ActorRef, ip: String)(
    implicit timeout: Timeout,
    executionContext: ExecutionContext)
    extends Api {

  def run(inputs: Inputs): Future[Ressource] = {
    (pasteActor ? InputsWithIp(inputs, ip)).mapTo[Ressource]
  }

  def save(inputs: Inputs): Future[Ressource] = run(inputs)

  def fetch(id: Long): Future[Option[FetchResult]] = {
    (pasteActor ? GetPaste(id)).mapTo[Option[FetchResult]]
  }

  def format(formatRequest: FormatRequest): Future[FormatResponse] = {
    (pasteActor ? formatRequest).mapTo[FormatResponse]
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

  val progressActor =
    system.actorOf(Props[ProgressActor], name = "ProgressActor")
  val pasteActor =
    system.actorOf(Props(new PasteActor(progressActor)), name = "PasteActor")

  def tmp(file: String) = Action { implicit request =>
    Ok.sendFile(new java.io.File("/tmp/" + file))
  }

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def index2(rest: String) = Action { implicit request =>
    Ok(views.html.index())
  }

  def progress(id: Long) = WebSocket.tryAccept[String] { request =>
    (progressActor ? MonitorProgress(id))
      .mapTo[MonitorChannel]
      .map(m => Right(m.value))
  }

  def autowireApi(path: String) = Action.async { implicit request =>
    val text = request.body.asText.getOrElse("")
    val api = new ApiImpl(pasteActor, request.remoteAddress)
    val autowireRequest =
      Request(path.split("/"), uread[Map[String, String]](text))
    AutowireServer.route[Api](api)(autowireRequest).map(buffer => Ok(buffer))
  }
}
