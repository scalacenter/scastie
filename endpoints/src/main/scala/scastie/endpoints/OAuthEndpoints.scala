package scastie.endpoints

import sttp.tapir._
import sttp.model.StatusCode
import sttp.model.headers.CacheDirective
import sttp.model.Header
import sttp.model.headers.CookieValueWithMeta
import sttp.model.HeaderNames

object OAuthEndpoints {
  val authorizationUrl = "https://github.com/login/oauth/authorize"
  val accessTokenUrl = "https://github.com/login/oauth/access_token"

  val sessionCookieName = "__Secure-Session-Token"
  val xsrfCookieName= "__Host-XSRF-Token"
  val xsrfHeaderName = "X-XSRF-Token"

  // TODO: Migrate to opaque types
  type ResponseCookies = (CookieValueWithMeta, CookieValueWithMeta)
  type ResponseCookiesWithRedirect = (String, CookieValueWithMeta, CookieValueWithMeta)
  type ErrorWithRedirect = (String, String)
  type JwtCookie  = String
  type XSRFCookie = String
  type XSRFHeader = String

  sealed trait Session
  case class NoSession() extends Session
  case class UserSession(jwtCookie: JwtCookie, xsrfCookie: XSRFCookie, xsrfHeader: XSRFHeader) extends Session
  case class OptionalUserSession(jwtCookie: Option[JwtCookie], xsrfCookie: Option[XSRFCookie], xsrfHeader: Option[XSRFHeader])

  object UserSession {
    def unapply(optionalUserSession: OptionalUserSession): Option[(JwtCookie, XSRFCookie, XSRFHeader)] = {
      for {
        jwtCookie <- optionalUserSession.jwtCookie
        xsrfCookie <- optionalUserSession.xsrfCookie
        xsrfHeader <- optionalUserSession.xsrfHeader
      } yield (jwtCookie, xsrfCookie, xsrfHeader)
    }
  }

  object NoSession {
    def unapply(optionalUserSession: OptionalUserSession): Option[NoSession] = {
      optionalUserSession match {
        case OptionalUserSession(None, None, None) => Some(NoSession())
        case _ => None
      }
    }
  }

  val login: PublicEndpoint[Option[String], Unit, String, Any] =
    endpoint.get
      .in("login")
      .in(header[Option[String]]("Referer"))
      .out(statusCode(StatusCode.SeeOther))
      .out(header(Header.cacheControl(CacheDirective.NoCache)))
      .out(header[String](HeaderNames.Location))

  val logout: PublicEndpoint[Unit, Unit, ResponseCookies, Any] =
    endpoint.get
      .in("logout")
      .out(statusCode(StatusCode.SeeOther))
      .out(header(Header.cacheControl(CacheDirective.NoCache)))
      .out(header(Header.location("/")))
      .out(setCookie(sessionCookieName))
      .out(setCookie(xsrfCookieName))

  val loginGithub: PublicEndpoint[(String, String), ErrorWithRedirect, ResponseCookiesWithRedirect, Any] =
    endpoint.get
      .in("callback")
      .in(query[String]("code"))
      .in(query[String]("state"))
      .out(header(Header.cacheControl(CacheDirective.NoCache)))
      .out(header[String](HeaderNames.Location))
      .out(setCookie(sessionCookieName))
      .out(setCookie(xsrfCookieName))
      .out(statusCode(StatusCode.Found))
      .errorOut(header[String](HeaderNames.Location))
      .errorOut(stringBody)

  val secureEndpoint: Endpoint[UserSession, Unit, String, Unit, Any] =
    endpoint
      .securityIn(auth.apiKey(cookie[JwtCookie](sessionCookieName)))
      .securityIn(cookie[XSRFCookie](xsrfCookieName))
      .securityIn(header[XSRFHeader](xsrfHeaderName))
      .mapSecurityInTo[UserSession]
      .errorOut(stringBody)

  val optionalSecureEndpoint: Endpoint[OptionalUserSession, Unit, String, Unit, Any] =
    endpoint
      .securityIn(auth.apiKey(cookie[Option[JwtCookie]](sessionCookieName)))
      .securityIn(cookie[Option[XSRFCookie]](xsrfCookieName))
      .securityIn(header[Option[XSRFHeader]](xsrfHeaderName))
      .mapSecurityInTo[OptionalUserSession]
      .errorOut(stringBody)

  val endpoints = login :: logout :: loginGithub :: Nil
}

