package controllers

import play.api.mvc._
import play.api.templates.Html

object Application extends Controller {

  def index = Action { implicit request =>
    val message = request.flash.get("error").map(Html(_)).getOrElse(Html("Enter your code and hit Submit:"))
    val paste = request.flash.get("paste").getOrElse("")
    Ok(views.html.index(message, paste))
  }

}