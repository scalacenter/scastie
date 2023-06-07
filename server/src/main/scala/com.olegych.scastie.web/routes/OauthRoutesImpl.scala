package com.olegych.scastie.web.routes

import com.olegych.scastie.api._
import play.api.libs.json.Json

import scala.concurrent.duration.DurationInt
import sttp.client3._
import cats.syntax.all._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scastie.endpoints.OAuthEndpoints
import java.time.Instant
import play.api.libs.json.OFormat
import sttp.model.headers.CookieValueWithMeta
import sttp.model.headers.Cookie
import java.util.UUID
import OAuthEndpoints._
import com.typesafe.config.ConfigFactory
import com.olegych.scastie.web.ServerConfig

case class AccessToken(token: String) extends AnyVal
object AccessToken {
  implicit val accessTokenFormat: OFormat[AccessToken] = Json.format[AccessToken]
}

case class GithubAccessToken(access_token: String) extends AnyVal
object GithubAccessToken {
  implicit val accessTokenFormat: OFormat[GithubAccessToken] = Json.format[GithubAccessToken]
}

class OAuthRoutesImpl(implicit ec: ExecutionContext) {

  val backend = HttpClientFutureBackend()

  private val config = ConfigFactory.load().getConfig("com.olegych.scastie.web.oauth2")
  val clientId = config.getString("client-id")
  val clientSecret = config.getString("client-secret")

  val loginRoute =
    OAuthEndpoints.login.serverLogicSuccess(maybeReferer => {
      val referer = maybeReferer.getOrElse("/")
      Future(s"$authorizationUrl?client_id=$clientId&state=$referer")
    })

  val logoutRoute =
    OAuthEndpoints.logout.serverLogicSuccess(_ => {
        Future {
          val sessionCookie = CookieValueWithMeta.unsafeApply(
            value = "",
            expires = Some(Instant.now),
            maxAge = Some(0),
            httpOnly = true,
            secure = true,
          )
          val xsrfCookie = sessionCookie.copy(httpOnly = false)
          (sessionCookie, xsrfCookie)
        }
      }
    )

  def loginGithubRoute(backend: SttpBackend[Future, Any]) =
    OAuthEndpoints.loginGithub.serverLogic(queryParameters => {
      val (code, referer) = queryParameters
      basicRequest
        .response(asStringAlways)
        .post(uri"$accessTokenUrl?client_id=$clientId&client_secret=$clientSecret&code=$code")
        .header("Accept", "application/json")
        .send(backend)
        .map(resp => {
          Json.parse(resp.body).asOpt[GithubAccessToken].toRight("Can't extract access token")
        })
        .flatMap(maybeAccessToken => {
          maybeAccessToken.flatTraverse { accessToken =>
            fetchUser(accessToken).map {
              case Some(user) => createSession(user)
              case None => Left("Could not fetch user info")
            }
          }
        }).map(_.bimap(
            error => (referer, error),
            cookies => (referer, cookies._1, cookies._2)
          )
        )
      }
    )

  private def fetchUser(accessToken: GithubAccessToken): Future[Option[User]] =
    basicRequest
      .response(asStringAlways)
      .get(uri"https://api.github.com/user")
      .header("Accept", "application/json")
      .header("Authorization", s"token ${accessToken.access_token}")
      .send(backend)
      .map(resp => Json.parse(resp.body).asOpt[User])

  private def createSession(user: User): Either[String, ResponseCookies] = {
    val now = Instant.now
    val responseCookies = for {
      sessionJWTCookie <- createJWTCookie(user, now)
      xsrfCookie <- createXSRFCookie(now)
    } yield (sessionJWTCookie, xsrfCookie)
    responseCookies.toRight("Could not create session")
  }

  private def createXSRFCookie(now: Instant): Option[CookieValueWithMeta] = {
    val expirationTime = now.plusSeconds(30.days.toSeconds)
    val sessionUUID = UUID.randomUUID()
    val signedCSRF: String = SessionManager.createXSRFToken(sessionUUID)
    CookieValueWithMeta.safeApply(
      value = signedCSRF,
      expires = Some(expirationTime),
      maxAge = Some(30.days.toSeconds),
      domain = Some(ServerConfig.hostname),
      httpOnly = false,
      path = Some("/"),
      sameSite = Some(Cookie.SameSite.Strict),
      secure = true,
    ).toOption
  }

  private def createJWTCookie(user: User, now: Instant): Option[CookieValueWithMeta] = {
    val expirationTime = now.plusSeconds(30.days.toSeconds)
    UUID.randomUUID()
    val claim: String = SessionManager.createJwtClaim(user, now)
    CookieValueWithMeta.safeApply(
      value = claim,
      expires = Some(expirationTime),
      maxAge = Some(30.days.toSeconds),
      domain = Some(ServerConfig.hostname),
      httpOnly = true,
      path = Some("/"),
      sameSite = Some(Cookie.SameSite.Strict),
      secure = true,
    ).toOption
  }

  val serverEndpoints = List(loginRoute, loginGithubRoute(backend), logoutRoute)
}
