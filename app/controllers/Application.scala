package controllers

import base.TemplatePastes
import play.api.i18n.Messages
import play.api.mvc._
import play.twirl.api.Html

import scala.util.Random
import scalaz.Scalaz._

object Application extends Controller {

  def index = Action { implicit request =>
    edit(TemplatePastes.default.content.orZero)
  }

  def edit(content: String)(implicit request: Request[AnyContent]): Result = {
    val message = Html(request.flash.get("error") | Messages("enter.code"))
    Ok(views.html.index(message, content)).withCookies(Cookie("uid", uid, maxAge = Some(Int.MaxValue)))
  }

  def uid(implicit request: Request[AnyContent]): String = {
    request.cookies.get("uid").map(_.value).getOrElse(Random.alphanumeric.take(30).mkString)
  }
}
