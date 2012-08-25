package controllers

import play.api.mvc._

object Application extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index(request.flash.get("paste").getOrElse("Your new application is ready.")))
  }

}