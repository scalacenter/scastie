package com.olegych.scastie.web.routes

import cats.syntax.all._
import com.olegych.scastie.api._
import com.typesafe.config.ConfigFactory
import pdi.jwt._
import play.api.libs.json.Json
import scastie.endpoints.OAuthEndpoints
import sttp.tapir._

import java.time.Instant
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Try

import OAuthEndpoints._

object SessionManager {
  private val jwtAlgorithm = JwtAlgorithm.HS256

  private val config = ConfigFactory.load().getConfig("com.olegych.scastie.web")
  private val sessionKey = config.getString("session-secret")
  private val xsrfKey = config.getString("xsrf-secret")
  private val random = new java.security.SecureRandom()

  val tokenDuration = 30.days

  implicit class Secure[Input, Output, R](endpoint: Endpoint[UserSession, Input, String, Output, R]) {
    def secure(implicit ec: ExecutionContext) = endpoint
      .serverSecurityLogic[User, Future](session => Future(authenticate(session)))
  }

  implicit class SecureOptionalEndpointExtension[Input, Output, R](endpoint: Endpoint[OptionalUserSession, Input, String, Output, R]) {
    def secure(implicit ec: ExecutionContext) = endpoint
      .serverSecurityLogic[Option[User], Future](optionalSession => Future(maybeAuthenticate(optionalSession)))
  }

  def createJwtClaim(user: User, now: Instant): String = {
    val expirationTime = now.plusSeconds(30.days.toSeconds)
    val claim = JwtClaim(
      expiration = Some(expirationTime.getEpochSecond),
      issuedAt = Some(now.getEpochSecond),
      content = Json.stringify(Json.toJson(user))
    )
    JwtJson.encode(claim, sessionKey, jwtAlgorithm)
  }

  def createXSRFToken(sessionUUID: UUID): String = {
    val randomValue = random.nextInt()
    val message = sessionUUID.toString + "!" + randomValue.toString
    val signHash = JwtUtils.sign(message, xsrfKey, jwtAlgorithm)
    JwtBase64.encodeString(signHash) + "#" + message
  }

  def authenticate(userSession: UserSession): Either[String, User] = {
    userSession match {
      case UserSession(jwtCookie, xsrfCookie, xsrfHeader) if verifyXSRF(xsrfCookie, xsrfHeader) => authenticateJwt(jwtCookie)
      case _ => "Invalid XSRF token".asLeft
    }
  }

  def maybeAuthenticate(maybeUserSession: OptionalUserSession): Either[String, Option[User]] = {
    maybeUserSession match {
      case UserSession(jwtCookie, xsrfCookie, xsrfHeader)
        if verifyXSRF(xsrfCookie, xsrfHeader) => authenticateJwt(jwtCookie).map(_.some)
      case NoSession(_) => None.asRight
      case _ => "Illegal authentication".asLeft
    }
  }

  def authenticateJwt(token: JwtCookie): Either[String, User] = {
    JwtJson
      .decodeAll(token, sessionKey, Seq(jwtAlgorithm))
      .toEither
      .leftMap(_.getMessage)
      .flatMap(decoded => Json.parse(decoded._2.content).asOpt[User].toRight("Invalid token"))
  }

  private def verifyXSRFSign(xsrfToken: String): Boolean =
    xsrfToken.split("#").toList match {
      case base64SignHash :: message :: Nil => {
        val maybeSignHashBytes = Try { JwtBase64.decode(base64SignHash) }.toOption
        val messageBytes = JwtUtils.bytify(message)
        maybeSignHashBytes.map(
          JwtUtils.verify(messageBytes, _, xsrfKey, jwtAlgorithm)
        ).getOrElse(false)
      }
      case _ => false
    }

  def verifyXSRF(xsrfCookie: XSRFCookie, xsrfHeader: XSRFHeader): Boolean =
    verifyXSRFSign(xsrfCookie) && verifyXSRFSign(xsrfHeader) && xsrfCookie == xsrfHeader
}
