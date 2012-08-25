package controllers

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._


object Pastes extends Controller {

  val pasteForm = Form(
    single(
      "paste" -> text
    )
  )

  def add = Action { implicit request =>
    Redirect(routes.Pastes.show("111"))
        .flashing("paste" -> ("hello" + pasteForm.bindFromRequest().apply("paste").value))
  }

  def show(id: String) = Action { implicit request =>
    Ok(views.html.index(id + " " + request.flash.get("paste").getOrElse("") + " Pasted!"))
  }
}