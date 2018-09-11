package com.olegych.scastie.web.oauth2

import com.olegych.scastie.web.PlayJsonSupport

import akka.http.scaladsl._
import akka.http.scaladsl.model._
import HttpMethods.POST
import headers._
import Uri._
import unmarshalling.Unmarshal

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.olegych.scastie.api.User

import scala.concurrent.Future
import com.typesafe.config.ConfigFactory
import play.api.libs.json.{OFormat, Reads}

case class AccessToken(access_token: String)

class Github(implicit system: ActorSystem, materializer: ActorMaterializer) extends PlayJsonSupport {
  import system.dispatcher

  import play.api.libs.json._
  implicit val formatUser: OFormat[User] = Json.format[User]
  implicit val readAccessToken: Reads[AccessToken] = Json.reads[AccessToken]

  private val config =
    ConfigFactory.load().getConfig("com.olegych.scastie.web.oauth2")
  val clientId: String = config.getString("client-id")
  private val clientSecret = config.getString("client-secret")
  private val redirectUri = config.getString("uri") + "/callback"

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
