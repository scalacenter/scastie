package org.scastie.server.utils

import scala.concurrent.{Future, ExecutionContext}
import scala.io.Source
import java.util.concurrent.ConcurrentHashMap
import scala.util.matching.Regex

object NightlyVersionFetcher {
  private val ttlMillis: Long = 60 * 60 * 1000 // 1 hour
  private val cache = new ConcurrentHashMap[String, (String, Long)]()

  /*
    Both Scala 2 and Scala 3 nightlies are now on the unified repo.scala-lang.org repository
    See: https://www.scala-lang.org/news/new-scala-nightlies-repo.html
  */
  private val urls = Map(
    "scala2" -> "https://repo.scala-lang.org/artifactory/maven-nightlies/org/scala-lang/scala-compiler/maven-metadata.xml",
    "scala3" -> "https://repo.scala-lang.org/artifactory/maven-nightlies/org/scala-lang/scala3-compiler_3/maven-metadata.xml"
  )

  def fetchRaw(api: String)(implicit ec: ExecutionContext): Future[String] = Future {
    val now = System.currentTimeMillis()
    val url = urls(api)
    val cached = Option(cache.get(api))
    cached match {
      case Some((data, timestamp)) if now - timestamp < ttlMillis =>
        data
      case _ =>
        val data = Source.fromURL(url).mkString
        cache.put(api, (data, now))
        data
    }
  }

  private val scala2NightlyRegex: Regex = raw"<version>(\d+\.\d+\.\d+-bin-\w+)</version>".r

  def getLatestScala2Nightly(prefix: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
    fetchRaw("scala2").map { data =>
      val versions = scala2NightlyRegex.findAllMatchIn(data).map(_.group(1)).toList
        .filter(_.startsWith(prefix))
      if (versions.nonEmpty) Some(versions.maxBy(parseVersionParts)) else None
    }
  }

  private def parseVersionParts(version: String): (Int, Int, Int, String) = {
    val parts = version.split("[.-]")
    val major = parts.lift(0).flatMap(_.toIntOption).getOrElse(0)
    val minor = parts.lift(1).flatMap(_.toIntOption).getOrElse(0)
    val patch = parts.lift(2).flatMap(_.toIntOption).getOrElse(0)
    val suffix = parts.drop(3).mkString("-")
    (major, minor, patch, suffix)
  }

  private val scala3NightlyRegex: Regex = raw"<version>(.+-bin-\d{8}-\w{7}-NIGHTLY)</version>".r

  def getLatestScala3Nightly(implicit ec: ExecutionContext): Future[Option[String]] = {
    fetchRaw("scala3").map { data =>
      val versions = scala3NightlyRegex.findAllMatchIn(data).map(_.group(1)).toList
      if (versions.nonEmpty) Some(versions.maxBy(parseVersionParts)) else None
    }
  }

}
