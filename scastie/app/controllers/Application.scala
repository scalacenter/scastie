package controllers

import com.olegych.scastie._
import web._
import oauth2.{Github, Users}

import Progress._
import api._

import upickle.default.{Reader, Writer, read => uread, write => uwrite}

import play.api.Play
import play.api.mvc._
import play.api.libs.json.JsValue
import play.api.libs.iteratee.{Iteratee, Enumerator}

import com.typesafe.config.ConfigFactory
import akka.util.Timeout
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask

import java.nio.ByteBuffer
import scala.concurrent.Future

import scala.util.Try
import java.util.UUID

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

  def fetch(id: Int): Future[Option[FetchResult]] = {
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

  def requireLogin(body: => Result)(
    implicit request: RequestHeader): Result = {

    requireLoginBase(body, TemporaryRedirect("/beta"))
  }
  def requireLoginAsync(body: => Future[Result])(
    implicit request: RequestHeader): Future[Result] = {

    requireLoginBase(body, Future(TemporaryRedirect("/beta")))
  }

  private def requireLoginBase[T](body: => T, fail: => T)(
    implicit request: RequestHeader): T = {
    val maybeUser =
      for {
        rawUuid <- request.session.get(OAuth2.sessionKey)
        uuid <- Try(UUID.fromString(rawUuid)).toOption
        user <- Users.get(uuid)
      } yield user

    maybeUser match {
      case Some(user) => body
      case None => fail
    }
  }

  def beta = Action { implicit request =>
    Ok(views.html.beta())
  }

  def index = Action { implicit request =>
    requireLogin(Ok(views.html.index()))
  }

  def index2(id: Int) = Action { implicit request =>
    requireLogin(Ok(views.html.index()))
  }

  def embedded = Action { implicit request =>
    requireLogin(Ok(views.html.embedded()))
  }

  def progress(id: Int) = {
    WebSocket.tryAccept[String] { implicit request =>
      requireLoginBase(
        (progressActor ? MonitorProgress(id))
          .mapTo[MonitorChannel]
          .map(m => Right(m.value)),
        Future(Left(Forbidden))
      )(request)
    }
  }

  // debug load balancer
  def loadBalancer = Action.async { implicit request =>
    (pasteActor ? LoadBalancerStateRequest).mapTo[LoadBalancerStateResponse].map(state =>
      requireLogin(Ok(views.html.loadbalancer(state)))
    )
  }

  def autowireApi(path: String) = Action.async { implicit request =>
    requireLoginAsync {
      val text = request.body.asText.getOrElse("")
      val api = new ApiImpl(pasteActor, request.remoteAddress)
      val autowireRequest =
        autowire.Core.Request(path.split("/"), uread[Map[String, String]](text))
      AutowireServer.route[Api](api)(autowireRequest).map(buffer => Ok(buffer))
    }
  }
}
