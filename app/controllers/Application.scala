package controllers

import play.api.mvc._
import play.api.templates.Html
import util.Random

object Application extends Controller {

  def index = Action { implicit request =>
    val message = request.flash.get("error").map(Html(_)).getOrElse(Html("Enter your code and hit Submit:"))
    val paste = request.flash.get("paste").getOrElse("object Main extends App {\n  \n}")
    Ok(views.html.index(message, paste)).withCookies(Cookie("uid", uid, maxAge = Some(Int.MaxValue)))
  }

  def uid(implicit request: Request[AnyContent]): String = {
    request.cookies.get("uid").map(_.value).getOrElse(Random.alphanumeric.take(30).mkString)
  }
}