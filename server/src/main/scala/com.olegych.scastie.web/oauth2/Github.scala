package com.olegych.scastie.web.oauth2

import com.olegych.scastie.proto.User

import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.Uri._
import akka.http.scaladsl.unmarshalling.Unmarshal

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import com.typesafe.config.ConfigFactory

case class AccessToken(access_token: String)

class Github(implicit system: ActorSystem, materializer: ActorMaterializer)
    extends Json4sSupport {
  import system.dispatcher

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
