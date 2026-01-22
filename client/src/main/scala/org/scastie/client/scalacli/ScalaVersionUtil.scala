package org.scastie.client.scalacli

import scala.concurrent.Future
import scala.scalajs.js
import org.scalajs.dom
import scala.util.matching.Regex
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scastie.buildinfo.BuildInfo

object ScalaVersionUtil {
    val location = dom.window.location
    val apiBase = if (location.hostname == "localhost") {
        location.protocol ++ "//" ++ location.hostname + ":" ++ "9000"
    } else if (location.protocol == "file:") {
        "http://localhost:9000"
    } else {
        "https://scastie.scala-lang.org"
    }

    val scala212Nightly       = "2.12.nightly"
    val scala213Nightly       = List("2.13.nightly", "2.nightly")
    val scala3Nightly         = "3.nightly"

    private val ttlMillis: Long = 60 * 60 * 1000 // 1 hour

    private val nightlyRegex: Regex =
        raw"<version>(.+-bin-\d{8}-\w{7}-NIGHTLY)</version>".r

    sealed trait ScalaNightly {
        def fetchLatest(prefix: String): Future[Option[String]]
        var cache : Option[(String, Long)]
    }

    object Scala3Nightly extends ScalaNightly {
        private val apiUrl = s"$apiBase/api/nightly-raw/scala3"
        var cache: Option[(String, Long)] = None
        def fetchLatest(prefix: String): Future[Option[String]] = {
            dom.fetch(apiUrl)
                .toFuture
                .flatMap(_.text().toFuture)
                .map { str =>
                    val cleaned = str.trim.replace("\"", "")
                    if (cleaned.nonEmpty) Some(cleaned) else None
                }
        }
    }

    object Scala2Nightly extends ScalaNightly {
        private val apiUrl = s"$apiBase/api/nightly-raw/scala2"
        var cache: Option[(String, Long)] = None
        def fetchLatest(prefix: String): Future[Option[String]] = {
            dom.fetch(s"$apiUrl/$prefix")
                .toFuture
                .flatMap(_.text().toFuture)
                .map { str =>
                    val cleaned = str.trim.replace("\"", "")
                    if (cleaned.nonEmpty) Some(cleaned) else None
                }
        }
    }

    private def resolveNightly(scalaVersion: ScalaNightly, prefix: String): Future[Option[String]] = {
        val now = System.currentTimeMillis()
        scalaVersion.cache match {
            case Some((version, timestamp)) if now - timestamp < ttlMillis =>
                Future.successful(Some(version))
            case _ =>
                scalaVersion.fetchLatest(prefix).map { optVersion =>
                    optVersion.foreach { v => scalaVersion.cache = Some((v, now)) }
                    optVersion
                }
        }
    }

    def resolveVersion(version: String): Future[String] = {
        val stableVersions = Map(
            "2"    -> BuildInfo.latest213,
            "2.10" -> BuildInfo.latest210,
            "2.11" -> BuildInfo.latest211,
            "2.12" -> BuildInfo.latest212,
            "2.13" -> BuildInfo.latest213,
            "3"    -> BuildInfo.stableNext,
            "3.1"  -> BuildInfo.latest31,
            "3.2"  -> BuildInfo.latest32,
            "3.3"  -> BuildInfo.latest33,
            "3.4"  -> BuildInfo.latest34,
            "3.5"  -> BuildInfo.latest35,
            "3.6"  -> BuildInfo.latest36,
            "3.7"  -> BuildInfo.latest37,
            "3.8"  -> BuildInfo.latest38
          )

        stableVersions.get(version) match {
            case Some(stable) => Future.successful(stable)
            case None if version == scala212Nightly =>
              resolveNightly(Scala2Nightly, "2.12").map(_.getOrElse(version))
            case None if scala213Nightly.contains(version) =>
              resolveNightly(Scala2Nightly, "2.13").map(_.getOrElse(version))
            case None if version == scala3Nightly =>
              resolveNightly(Scala3Nightly, "3").map(_.getOrElse(version))
            case _ =>
              Future.successful(version)
          }
    }
}
