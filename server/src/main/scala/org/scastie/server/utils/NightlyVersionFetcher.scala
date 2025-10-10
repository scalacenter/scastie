package org.scastie.server.utils

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.matching.Regex

import io.circe._
import io.circe.parser._

object NightlyVersionFetcher {
  private val ttlMillis: Long = 60 * 60 * 1000 // 1 hour
  private val cache = new ConcurrentHashMap[String, (String, Long)]()

  private val urls = Map(
    "scala2" -> "https://scala-ci.typesafe.com/ui/api/v1/ui/nativeBrowser/scala-integration/org/scala-lang/scala-compiler",
    "scala3" -> "https://repo1.maven.org/maven2/org/scala-lang/scala3-compiler_3/maven-metadata.xml"
  )

  def fetchRaw(api: String)(
    implicit ec: ExecutionContext
  ): Future[String] = Future {
    val now = System.currentTimeMillis()
    val url = urls(api)
    val cached = Option(cache.get(api))
    cached match {
      case Some((data, timestamp)) if now - timestamp < ttlMillis => data
      case _                                                      =>
        val data = Source.fromURL(url).mkString
        cache.put(api, (data, now))
        data
    }
  }

  def getLatestScala2Nightly(prefix: String)(
    implicit ec: ExecutionContext
  ): Future[Option[String]] = {
    fetchRaw("scala2").map { data =>
      val result = for {
        json <- parse(data)
        children <- json.hcursor.downField("children").as[List[Json]]
        names = children.flatMap(_.hcursor.get[String]("name").toOption)
        filtered = names
          .filter(_.startsWith(prefix))
          .filter(_.contains("bin"))
          .filterNot(_.contains("pre"))
      } yield filtered

      result match {
        case Right(versions) if versions.nonEmpty => Some(versions.sorted.last)
        case _                                    => None
      }
    }
  }

  def getLatestScala3Nightly(
    implicit ec: ExecutionContext
  ): Future[Option[String]] = {
    val nightlyRegex: Regex = raw"<version>(.+-bin-\d{8}-\w{7}-NIGHTLY)</version>".r

    fetchRaw("scala3").map { data =>
      val versions = nightlyRegex.findAllMatchIn(data).map(_.group(1)).toList
      if (versions.nonEmpty) Some(versions.sorted.last) else None
    }
  }

}
