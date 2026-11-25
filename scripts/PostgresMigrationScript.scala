//> using scala 3

//> using dep org.scastie:api_2.13:1.0.0-SNAPSHOT
//> using dep org.scastie:storage_2.13:1.0.0-SNAPSHOT

//> using dep com.lihaoyi::pprint:latest.release

import org.scastie.storage.postgres.PostgresContainer
import org.scastie.storage.postgres.Snippet
import org.scastie.storage.MongoSnippet

import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}

import io.circe.*
import io.circe.parser.*
import org.mongodb.scala._
import org.mongodb.scala.model.Filters
import org.scastie.api.*

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*

@main def migrate = {
  val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
  rootLogger.setLevel(Level.INFO)
  LoggerFactory.getLogger("com.zaxxer.hikari").asInstanceOf[Logger].setLevel(Level.WARN)
  LoggerFactory.getLogger("org.postgresql").asInstanceOf[Logger].setLevel(Level.WARN)
  LoggerFactory.getLogger("scalasql").asInstanceOf[Logger].setLevel(Level.WARN)
  LoggerFactory.getLogger("org.scastie.storage").asInstanceOf[Logger].setLevel(Level.WARN)
  System.setProperty("org.mongodb.driver.level", "WARN")

  val mongoClient: MongoClient = MongoClient("mongodb://localhost:27017/scastie")
  val mongoDatabase: MongoDatabase = mongoClient.getDatabase("snippets")
  val mongoSnippets = mongoDatabase.getCollection[Document]("snippets")

  val pgContainer: PostgresContainer = new PostgresContainer(defaultConfig = true)

  def fromBson[T](obj: Document)(implicit decoder: Decoder[T]): Option[T] =
    decode[T](obj.toJson()) match {
      case Right(value) => Some(value)
      case Left(err) =>
        pprint.pprintln(s"Could not deserialize: ${err.getMessage}")
        None
    }

  def toSnippet(mongo: MongoSnippet): Snippet =
    Snippet(
      simpleSnippetId = mongo.simpleSnippetId,
      username = mongo.user,
      snippetId = mongo.snippetId,
      inputs = mongo.inputs,
      progresses = mongo.progresses,
      scalaJsContent = mongo.scalaJsContent,
      scalaJsSourceMapContent = mongo.scalaJsSourceMapContent,
      time = mongo.time
    )

  val failedSnippets = ArrayBuffer.empty[String]

  def saveSnippetInNewDatabase(snippets: Seq[Snippet]): Future[Unit] = {
    Future.traverse(snippets.grouped(10).toSeq) { batch =>
      Future.traverse(batch) { s =>
        (for {
          _ <- pgContainer.insertWithExistingId(s.snippetId, s.inputs)
          _ <- Future.sequence(s.progresses.map(pgContainer.appendOutput))
        } yield ()).recover { case e: Throwable =>
          pprint.pprintln(s"Failed to migrate snippet ${s.snippetId.url}: ${e.getMessage}")
          failedSnippets += s.snippetId.url
        }
      }
    }.map(_ => ())
  }

  def migrateSnippets(originalSize: Int): Future[Seq[String]] = {
    pprint.pprintln(s"Starting migration of $originalSize snippets")

    var count = 0L
    var progress = 0
    val allUrls = scala.collection.mutable.ArrayBuffer.empty[String]

    def step(): Future[Unit] = {
      if (count >= originalSize) {
        Future.unit
      } else {
        mongoSnippets
          .find()
          .skip(count.toInt)
          .limit(1000)
          .toFuture()
          .flatMap { docs =>
            val parsed = docs.flatMap(fromBson[MongoSnippet])

            parsed.foreach { s =>
              if (s.user.isEmpty) pprint.pprintln(s"Skipping anonymous snippet ${s.snippetId.url}")
              else pprint.pprintln(s"Migrating snippet ${s.snippetId.url}")
            }

            val snippets = parsed
              .filter(_.user.nonEmpty)
              .map(toSnippet)

            count += docs.size

            val cProgress = (count * 100) / originalSize
            if (cProgress > progress) {
              progress = cProgress.toInt
              pprint.pprintln(s"Progress: [$progress% / 100%]")
            }

            allUrls ++= snippets.map(_.snippetId.url)
            saveSnippetInNewDatabase(snippets).flatMap(_ => step())
          }
      }
    }

    step().map(_ => allUrls.toSeq)
  }

  def verifySnippets(migratedSnippetUrls: Seq[String]): Future[Unit] = {
    pprint.pprintln(s"Verifying ${migratedSnippetUrls.size} snippets...")

    var count = 0L
    var progress = 0
    var errors = 0

    def step(): Future[Unit] = {
      if (count >= migratedSnippetUrls.size) {
        pprint.pprintln(s"Verification complete! Total errors: $errors")
        Future.unit
      } else {
        val batch = migratedSnippetUrls.slice(count.toInt, (count + 100).toInt)

        Future.traverse(batch) { url =>
          val snippetId = SnippetId.fromString(url)

          for {
            mongoOpt <- {
              mongoSnippets
                .find(Filters.eq("snippetId.base64UUID", snippetId.base64UUID))
                .headOption()
                .map(_.flatMap(fromBson[MongoSnippet]))
            }
            pgOpt <- Future.successful(pgContainer.readPostgresSnippet(snippetId))
          } yield (url, mongoOpt, pgOpt)
        }.flatMap { results =>
          results.foreach { case (url, mongoOpt, pgOpt) =>
            (mongoOpt, pgOpt) match {
              case (Some(mongo), Some(pg)) =>
                val mongoCode = mongo.inputs.code
                val pgCode = pg.inputs.code

                if (mongoCode != pgCode) {
                  errors += 1
                  pprint.pprintln(s"Snippet $url code mismatch")
                } else {
                  pprint.pprintln(s"Snippet $url verified successfully")
                }

              case (None, Some(_)) =>
                errors += 1
                pprint.pprintln(s"Snippet $url missing in MongoDB")

              case (Some(_), None) =>
                errors += 1
                pprint.pprintln(s"Snippet $url missing in Postgres")

              case (None, None) =>
                errors += 1
                pprint.pprintln(s"Snippet $url missing in both databases")
            }
          }

          count += batch.size
          val cProgress = (count * 100) / migratedSnippetUrls.size
          if (cProgress > progress) {
            progress = cProgress.toInt
            pprint.pprintln(s"Verification Progress: [$progress% / 100%]")
          }

          step()
        }
      }
    }

    step()
  }

  try {
    val snippetsNumber = Await.result(mongoSnippets.countDocuments().head, Duration.Inf)

    pprint.pprintln(s"Starting migration of $snippetsNumber snippets...")

    val migrationFuture = for {
      migratedUrls <- migrateSnippets(snippetsNumber.toInt)
      _ <- verifySnippets(migratedUrls)
    } yield ()

    Await.result(migrationFuture, Duration.Inf)

    if (failedSnippets.nonEmpty) {
      pprint.pprintln(s"${failedSnippets.size} snippets failed to migrate:")
      failedSnippets.foreach(url => pprint.pprintln(s"  - $url"))
    }

  } catch {
    case t: Throwable => t.printStackTrace()
  } finally {
    mongoClient.close()
  }
}
