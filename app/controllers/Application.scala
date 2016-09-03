package controllers

import api._

import play.api.mvc._
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import java.nio.ByteBuffer
import upickle.default.{read => uread}

class ApiImpl() extends Api {
  def run(code: String): Future[String] = Future.successful(code)
}

object Application extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  private val api = new ApiImpl()

  def autowireApi(path: String) = Action.async { implicit request =>
    // get the request body as ByteString

    val text = request.body.asText.getOrElse("")
    
    AutowireServer.route[Api](api)(
      autowire.Core.Request(path.split("/"), uread[Map[String, String]](text))
    ).map(buffer => {
      // val data = Array.ofDim[Byte](buffer.remaining())
      // buffer.get(data)
      println(buffer)
      Ok(buffer)
    })
  }
}
