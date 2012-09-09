package controllers

import play.api.mvc._
import play.api.templates.Html

object Application extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index(Html(request.flash.get("paste").getOrElse("Your new application is ready."))))
  }

}