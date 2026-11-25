package org.scastie.web.oauth2

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl._
import org.apache.pekko.http.scaladsl.model.HttpMethods.POST
import org.apache.pekko.http.scaladsl.model.Uri._
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers._
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.scastie.api.User
import io.circe._
import io.circe.generic.semiauto._
import com.typesafe.config.ConfigFactory
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport

import scala.concurrent.Future

import org.scastie.api.UserData

case class AccessToken(access_token: String)

class Github(implicit system: ActorSystem) extends FailFastCirceSupport {
  import system.dispatcher

  implicit val readAccessToken: Decoder[AccessToken] = deriveDecoder[AccessToken]

  private val config =
    ConfigFactory.load().getConfig("org.scastie.web.oauth2")
  val clientId: String = config.getString("client-id")
  private val clientSecret = config.getString("client-secret")
  private val redirectUri = config.getString("uri") + "/callback"

  def getUserDataWithOauth2(code: String): Future[UserData] = {
    def access = {
      Http()
        .singleRequest(
          HttpRequest(
            method = POST,
            uri = Uri("https://github.com/login/oauth/access_token").withQuery(
              Query(
                "client_id" -> clientId,
                "client_secret" -> clientSecret,
                "code" -> code,
                "redirect_uri" -> redirectUri
              )
            ),
            headers = List(Accept(MediaTypes.`application/json`))
          )
        )
        .flatMap(
          response => Unmarshal(response).to[AccessToken].map(_.access_token)
        )
    }

    access.flatMap(info)
  }

  private def info(token: String): Future[UserData] = {
    def fetchGithub(path: Path, query: Query = Query.Empty) = {
      HttpRequest(
        uri = Uri(s"https://api.github.com").withPath(path).withQuery(query),
        headers = List(Authorization(GenericHttpCredentials("token", token)))
      )
    }

    val userData = 
      Http()
      .singleRequest(fetchGithub(Path.Empty / "user"))
      .flatMap(response => Unmarshal(response).to[User])

    val userOrgs = 
      Http()
        .singleRequest(fetchGithub(Path.Empty / "user" / "orgs"))
        .flatMap(response => Unmarshal(response).to[List[User]])

    for {
      user <- userData
      switchableUsers <- userOrgs
    } yield {
      UserData(user, switchableUsers)
    }
  }
}
