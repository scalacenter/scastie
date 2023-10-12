package com.olegych.scastie.client.components.editor

import scastie.api._
import com.olegych.scastie.client._
import japgolly.scalajs.react._
import org.scalajs.dom

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import scalajs.js
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalajs.js.Thenable.Implicits._
import js.JSConverters._

import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.disjunctionCodecs.decoderEither

trait MetalsClient {
  val updateStatus: MetalsStatus ~=> Callback
  val metalsStatus: MetalsStatus
  val dependencies: Set[ScalaDependency]
  val target: ScalaTarget
  val isWorksheetMode: Boolean
  val isEmbedded: Boolean
  val code: String
  val scastieMetalsOptions = ScastieMetalsOptions(dependencies, target, code)


  private val isConfigurationSupported: Future[Boolean] = {
    if (metalsStatus == MetalsDisabled || isEmbedded) Future.successful(false)
    else {
      updateStatus(MetalsLoading).runNow()
      println(scastieMetalsOptions)
      val res = makeRequest(scastieMetalsOptions, "isConfigurationSupported").map(maybeText =>
        parseMetalsResponse[ScastieMetalsOptions](maybeText).map(Right(_)).getOrElse(Left(maybeText))
      )
      res.onComplete {
        case Success(Right(opt)) => {
          updateStatus(MetalsReady).runNow()
        }
        case Success(Left(details)) => updateStatus(NetworkError(s"Error sent from server: $details")).runNow()
        case Failure(exception) => updateStatus(NetworkError(exception.getMessage)).runNow()
        case _ =>
      }
      res.map { _.isRight }
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

  protected def toLSPRequest(code: String, offset: Int): LSPRequestDTO = {
    val offsetParams = ScastieOffsetParams(code, offset, isWorksheetMode)
    LSPRequestDTO(scastieMetalsOptions, offsetParams)
  }

  protected def makeRequest[A](req: A, endpoint: String)(implicit writes: Encoder[A]): Future[Option[String]] = {
    val location = dom.window.location
    // this is workaround until we migrate all services to proper docker setup or unify the servers
    val apiBase = if (location.hostname == "localhost") {
      location.protocol ++ "//" ++ location.hostname + ":" ++ "8000"
    } else ""

    // We don't support metals in embedded so we don't need to map server url
    val request = dom.fetch(s"$apiBase/metals/$endpoint", js.Dynamic.literal(
      body = req.asJson.noSpaces,
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

  protected def parseMetalsResponse[A](maybeJsonText: Option[String])(implicit readsB: Decoder[A]): Option[A] = {
    maybeJsonText.flatMap(jsonText => {
      decode[Either[FailureType, A]](jsonText).toOption match {
        case None =>
          None
        case Some(Left(PresentationCompilerFailure(msg))) =>
          updateStatus(MetalsConfigurationError(msg)).runNow()
          None
        case Some(Left(err)) =>
          println(err.msg)
          None
        case Some(Right(value)) =>
          updateStatus(MetalsReady)
          Some(value)
      }
    })
  }
}
