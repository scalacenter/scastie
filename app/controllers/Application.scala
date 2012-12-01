package controllers

import play.api.mvc._
import play.api.templates.Html

object Application extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index(Html("Enter your code and hit Submit:")))
  }

}