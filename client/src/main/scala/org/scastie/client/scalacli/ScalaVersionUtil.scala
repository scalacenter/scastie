package org.scastie.client.scalacli

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.matching.Regex

import org.scalajs.dom

object ScalaVersionUtil {
  val location = dom.window.location

  val apiBase =
    if (location.hostname == "localhost") {
      location.protocol ++ "//" ++ location.hostname + ":" ++ "9000"
    } else if (location.protocol == "file:") {
      "http://localhost:9000"
    } else {
      "https://scastie.scala-lang.org"
    }

  val scala212Nightly = "2.12.nightly"
  val scala213Nightly = List("2.13.nightly", "2.nightly")
  val scala3Nightly = "3.nightly"

  private val ttlMillis: Long = 60 * 60 * 1000 // 1 hour

  private val nightlyRegex: Regex = raw"<version>(.+-bin-\d{8}-\w{7}-NIGHTLY)</version>".r

  sealed trait ScalaNightly {
    def fetchLatest(prefix: String): Future[Option[String]]
    var cache: Option[(String, Long)]
  }

  object Scala3Nightly extends ScalaNightly {
    private val apiUrl = s"$apiBase/api/nightly-raw/scala3"
    var cache: Option[(String, Long)] = None

    def fetchLatest(prefix: String): Future[Option[String]] = {
      dom
        .fetch(apiUrl)
        .toFuture
        .flatMap(_.text().toFuture)
        .map { str =>
          val trimmed = str.trim
          if (trimmed.nonEmpty) Some(trimmed) else None
        }
    }

  }

  object Scala2Nightly extends ScalaNightly {
    private val apiUrl = s"$apiBase/api/nightly-raw/scala2"
    var cache: Option[(String, Long)] = None

    def fetchLatest(prefix: String): Future[Option[String]] = {
      dom
        .fetch(s"$apiUrl/$prefix")
        .toFuture
        .flatMap(_.text().toFuture)
        .map { str =>
          val trimmed = str.trim
          if (trimmed.nonEmpty) Some(trimmed) else None
        }
    }

  }

  private def resolveNightly(scalaVersion: ScalaNightly, prefix: String): Future[Option[String]] = {
    val now = System.currentTimeMillis()
    scalaVersion.cache match {
      case Some((version, timestamp)) if now - timestamp < ttlMillis => Future.successful(Some(version))
      case _ => scalaVersion.fetchLatest(prefix).map { optVersion =>
          optVersion.foreach { v => scalaVersion.cache = Some((v, now)) }
          optVersion
        }
    }
  }

  def resolveVersion(version: String): Future[String] = version match {
    case v if v == scala212Nightly        => resolveNightly(Scala2Nightly, "2.12").map(_.getOrElse(v))
    case v if scala213Nightly.contains(v) => resolveNightly(Scala2Nightly, "2.13").map(_.getOrElse(v))
    case v if v == scala3Nightly          => resolveNightly(Scala3Nightly, "3").map(_.getOrElse(v))
    case _                                => Future.successful(version)
  }

}
