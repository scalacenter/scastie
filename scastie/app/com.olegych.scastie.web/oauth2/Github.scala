package com.olegych.scastie
package web
package oauth2

import akka.http.scaladsl._
import akka.http.scaladsl.model._
import HttpMethods.POST
import headers._
import Uri._
import unmarshalling.{Unmarshal, Unmarshaller}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._

import scala.concurrent.Future

import play.api.Play

case class AccessToken(access_token: String)
case class User(login: String, name: Option[String], avatar_url: String)


class Github() extends Json4sSupport {
  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val materializer = ActorMaterializer()


  private val config = Play.current.configuration
  val clientId = config.getString("oauth2.client-id").get
  private val clientSecret = config.getString("oauth2.client-secret").get
  private val redirectUri = config.getString("oauth2.uri").get + "/callback/done"

  private val poolClientFlow = Http().cachedHostConnectionPoolHttps[HttpRequest]("api.github.com")

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
              )),
            headers = List(Accept(MediaTypes.`application/json`))
          ))
        .flatMap(response => Unmarshal(response).to[AccessToken].map(_.access_token))
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
