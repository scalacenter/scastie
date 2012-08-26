package controllers

import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import scala.sys.process._


object Pastes extends Controller {

  val pasteForm = Form(
    single(
      "paste" -> text
    )
  )

  def add = Action { implicit request =>
    val res = sbt("hello").lines_!.toList.mkString("\n")
    Redirect(routes.Pastes.show("111"))
        .flashing("paste" -> (res + pasteForm.bindFromRequest().apply("paste").value))
  }


  def sbt(command: String): String = {
    (if (org.apache.commons.lang.SystemUtils.IS_OS_WINDOWS) "xsbt.cmd " else "xsbt.sh ") + command
  }
  def show(id: String) = Action { implicit request =>
    Ok(views.html.index(id + " " + request.flash.get("paste").getOrElse("") + " Pasted!"))
  }
}