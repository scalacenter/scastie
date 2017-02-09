package controllers

import play.api.mvc._
import play.api.Play

import com.olegych.scastie.web.oauth2.{Github, Users}

import play.api.libs.concurrent.Execution.Implicits._

object OAuth2 extends Controller {

  val github = new Github()

  def login = Action { implicit request =>
    TemporaryRedirect(
      s"https://github.com/login/oauth/authorize?client_id=${github.clientId}"
    )
  }

  def logout = Action { implicit request =>
    TemporaryRedirect("/").withNewSession
  }

  val sessionKey = "user-uuid"

  def callback(code: String, state: Option[String]) = Action.async { implicit request =>
    import play.api.Play.current

    github.getUserWithOauth2(code).map{user =>

      Users.add(user.login)
      val uuid = Users.storeSession(user)

      TemporaryRedirect("/").withSession(
        request.session + (sessionKey -> uuid.toString)
      )
    }
  }
}
