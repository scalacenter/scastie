package com.olegych.scastie.web.oauth2

import com.olegych.scastie.web.PlayJsonSupport

import akka.http.scaladsl._
import akka.http.scaladsl.model._
import HttpMethods.POST
import headers._
import Uri._
import unmarshalling.Unmarshal

import akka.actor.ClassicActorSystemProvider
import com.olegych.scastie.api.User

import scala.concurrent.{ExecutionContext, Future}
import com.typesafe.sslconfig.util.{ConfigLoader, EnrichedConfig}
import com.olegych.scastie.util.ConfigLoaders._

import java.nio.file.{Path => JPath}

case class AccessToken(access_token: String)

case class Oauth2Conf(
  usersFile: JPath,
  sessionsFile: JPath,
  clientId: String,
  clientSecret: String,
  uri: String,
)

object Oauth2Conf {
  implicit val loader: ConfigLoader[Oauth2Conf] = (c: EnrichedConfig) => Oauth2Conf(
    c.get[JPath]("users-file"),
    c.get[JPath]("sessions-file"),
    c.get[String]("client-id"),
    c.get[String]("client-secret"),
    c.get[String]("uri")
  )
}

class Github(config: Oauth2Conf)(implicit system: ClassicActorSystemProvider) extends PlayJsonSupport {
  implicit def ec: ExecutionContext = system.classicSystem.dispatcher

  import play.api.libs.json._
  implicit val formatUser: OFormat[User] = Json.format[User]
  implicit val readAccessToken: Reads[AccessToken] = Json.reads[AccessToken]

  val clientId: String = config.clientId
  private val redirectUri = config.uri + "/callback"

  def getUserWithToken(token: String): Future[User] = info(token)
  def getUserWithOauth2(code: String): Future[User] = {
    def access = {
      Http()
        .singleRequest(
          HttpRequest(
            method = POST,
            uri = Uri("https://github.com/login/oauth/access_token").withQuery(
              Query(
                "client_id" -> clientId,
                "client_secret" -> config.clientSecret,
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

  private def info(token: String): Future[User] = {
    def fetchGithub(path: Path, query: Query = Query.Empty) = {
      HttpRequest(
        uri = Uri(s"https://api.github.com").withPath(path).withQuery(query),
        headers = List(Authorization(GenericHttpCredentials("token", token)))
      )
    }

    Http()
      .singleRequest(fetchGithub(Path.Empty / "user"))
      .flatMap(response => Unmarshal(response).to[User])
  }
}
