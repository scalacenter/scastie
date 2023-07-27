package com.olegych.scastie.client.components.editor

import com.olegych.scastie.api
import com.olegych.scastie.api.EitherFormat.JsEither._
import com.olegych.scastie.client._
import japgolly.scalajs.react._
import org.scalajs.dom
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.Writes

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import scalajs.js
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalajs.js.Thenable.Implicits._
import js.JSConverters._

trait MetalsClient {
  val updateStatus: MetalsStatus ~=> Callback
  val metalsStatus: MetalsStatus
  val dependencies: Set[api.ScalaDependency]
  val target: api.ScalaTarget
  val isWorksheetMode: Boolean
  val isEmbedded: Boolean
  val scastieMetalsOptions = api.ScastieMetalsOptions(dependencies, target)

  private val isConfigurationSupported: Future[Boolean] = {
    if (metalsStatus == MetalsDisabled || isEmbedded) Future.successful(false)
    else {
      updateStatus(MetalsLoading).runNow()
      val res = makeRequest(scastieMetalsOptions, "isConfigurationSupported").map(maybeText =>
        parseMetalsResponse[Boolean](maybeText).getOrElse(false)
      )
      res.onComplete {
        case Success(true) => updateStatus(MetalsReady).runNow()
        case Failure(exception) => updateStatus(NetworkError(exception.getMessage)).runNow()
        case _ =>
      }
      res
    }
  }

  /*
   * Runs function `f` only when current scastie configuration is supported.
   */
  protected def ifSupported[A](f: => Future[Option[A]]): js.Promise[Option[A]] = {
    isConfigurationSupported.flatMap(isSupported => {
      if (isSupported) {
        updateStatus(MetalsLoading).runNow()
        val res = f.map(Option(_))
        res.onComplete(_ => updateStatus(MetalsReady).runNow())
        res
      } else
        Future.successful(None)
    }).map(_.flatten).toJSPromise
  }

  protected def toLSPRequest(code: String, offset: Int): api.LSPRequestDTO = {
    val offsetParams = api.ScastieOffsetParams(code, offset, isWorksheetMode)
    api.LSPRequestDTO(scastieMetalsOptions, offsetParams)
  }

  protected def makeRequest[A](req: A, endpoint: String)(implicit writes: Writes[A]): Future[Option[String]] = {
    val location = dom.window.location
    // this is workaround until we migrate all services to proper docker setup or unify the servers
    val apiBase = if (location.hostname == "localhost") {
      location.protocol ++ "//" ++ location.hostname + ":" ++ "8000"
    } else ""

    // We don't support metals in embedded so we don't need to map server url
    val request = dom.fetch(s"$apiBase/metals/$endpoint", js.Dynamic.literal(
      body = Json.toJson(req).toString,
      method = dom.HttpMethod.POST
    ).asInstanceOf[dom.RequestInit])

    for {
      res <- request
      text <- res.text()
    } yield {
      if (res.ok) Some(text)
      else {
        updateStatus(NetworkError(text))
        None
      }
    }
  }

  protected def parseMetalsResponse[A](maybeJsonText: Option[String])(implicit readsB: Reads[A]): Option[A] = {
    maybeJsonText.flatMap(jsonText => {
      Json.parse(jsonText).asOpt[Either[api.FailureType, A]] match {
        case None =>
          None
        case Some(Left(api.PresentationCompilerFailure(msg))) =>
          updateStatus(MetalsConfigurationError(msg)).runNow()
          None
        case Some(Left(api.NoResult(msg))) =>
          None
        case Some(Right(value)) =>
          updateStatus(MetalsReady)
          Some(value)
      }
    })
  }
}
