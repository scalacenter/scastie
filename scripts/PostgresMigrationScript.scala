//> using scala 3

//> using dep org.scastie.old::api::0.30.0-SNAPSHOT
//> using dep org.scastie.old:storage_2.13:0.30.0-SNAPSHOT

//> using dep org.scastie:api_2.13:1.0.0-SNAPSHOT
//> using dep org.scastie:storage_2.13:1.0.0-SNAPSHOT

//> using dep com.typesafe:config:latest.release
//> using dep com.lihaoyi::pprint:latest.release



import org.scastie.storage.postgres.PostgresContainer
import org.scastie.storage.postgres.Snippet

import org.slf4j.LoggerFactory
import ch.qos.logback.classic.{Level, Logger}

import com.olegych.scastie.api.AttachedDom
import com.olegych.scastie.api.Html
import com.olegych.scastie.api.ScalaTarget.Js
import com.olegych.scastie.api.ScalaTarget.Jvm
import com.olegych.scastie.api.ScalaTarget.Native
import com.olegych.scastie.api.ScalaTarget.Scala3
import com.olegych.scastie.api.ScalaTarget.Typelevel
import com.olegych.scastie.api.Value
import com.olegych.scastie.{api => oldApi}
import com.olegych.scastie.{storage => oldStorage}
import com.typesafe.config.ConfigFactory
import io.circe.Encoder
import io.circe.*
import org.mongodb.scala._
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.model._
import org.scastie.runtime.{api => runtimeApi}
import org.scastie.storage.MongoSnippet.mongoSnippetEncoder
import org.scastie.{api => newApi}
import org.scastie.{storage => newStorage}
import play.api.libs.json.Json
import play.api.libs.json.Reads

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.util.Try

@main def migrate = {
  val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
  rootLogger.setLevel(Level.INFO)
  LoggerFactory.getLogger("com.zaxxer.hikari").asInstanceOf[Logger].setLevel(Level.WARN)
  LoggerFactory.getLogger("org.postgresql").asInstanceOf[Logger].setLevel(Level.WARN)
  LoggerFactory.getLogger("scalasql").asInstanceOf[Logger].setLevel(Level.WARN)
  LoggerFactory.getLogger("org.scastie.storage").asInstanceOf[Logger].setLevel(Level.WARN)
  System.setProperty("org.mongodb.driver.level", "WARN")
  val mongoUri = {
      val config       = ConfigFactory.load().getConfig("scastie.mongodb")
      val user         = config.getString("user")
      val password     = config.getString("password")
      val databaseName = config.getString("database")
      val host         = config.getString("host")
      val port         = config.getInt("port")
      s"mongodb://$user:$password@$host:$port/$databaseName"
    //   "mongodb://127.0.0.1:27017/?directConnection=true&serverSelectionTimeoutMS=2000&appName=mongosh+1.6.1"
    }
  val mongoClient: MongoClient =
    MongoClient(mongoUri)

  val oldDatabase: MongoDatabase = mongoClient.getDatabase("snippets")
  val oldSnippets = oldDatabase.getCollection[Document]("snippets")
  
  val pgContainer: PostgresContainer =
    new PostgresContainer(defaultConfig = true)

  def fromBson[T](obj: Document)(implicit reads: Reads[T]): Option[T] =
      pprint(s"Deserializing BSON: ${obj.toJson().take(10)}")
      Json.parse(obj.toJson()).asOpt[T]

  def convertType(o: oldApi.ProcessOutputType): newApi.ProcessOutputType =
    o match
      case oldApi.ProcessOutputType.StdErr => newApi.ProcessOutputType.StdErr
      case oldApi.ProcessOutputType.StdOut => newApi.ProcessOutputType.StdOut

  def convertSeverity(o: oldApi.Severity): newApi.Severity =
    o match
      case oldApi.Info => newApi.Info
      case oldApi.Warning => newApi.Warning
      case oldApi.Error => newApi.Error

  def convertInstrumentation(instrumentation: oldApi.Instrumentation): runtimeApi.Instrumentation =
    val newPosition = runtimeApi.Position(instrumentation.position.start, instrumentation.position.end)
    val newRender = instrumentation.render match
      case Value(v, className) => runtimeApi.Value(v, className)
      case Html(a, folded) => runtimeApi.Html(a, folded)
      case AttachedDom(uuid, folded) => runtimeApi.AttachedDom(uuid, folded)

    runtimeApi.Instrumentation(newPosition, newRender)

  def convertTarget(target: oldApi.ScalaTarget): newApi.SbtScalaTarget =
    target match
      case Jvm(scalaVersion) => newApi.Scala2(scalaVersion)
      case Typelevel(scalaVersion) => newApi.Typelevel(scalaVersion)
      case Js(scalaVersion, scalaJsVersion) => newApi.Js(scalaVersion, scalaJsVersion)
      case Native(scalaVersion, scalaNativeVersion) => newApi.Native(scalaVersion, scalaNativeVersion)
      case Scala3(scalaVersion) => newApi.Scala3(scalaVersion)

  def convertLibrariesFromList(libs: List[(oldApi.ScalaDependency, oldApi.Project)], newTarget: newApi.SbtScalaTarget): List[(newApi.ScalaDependency, newApi.Project)] =
    libs.map { (l, pro) =>
      val newL = newApi.ScalaDependency(l.groupId, l.artifact, newTarget, l.version)
      val newPro = newApi.Project(pro.organization, pro.repository, pro.logo, pro.artifacts)

      newL -> newPro
    }

  def convertSnippetId(old: oldApi.SnippetId): newApi.SnippetId =
    val newUser: Option[newApi.SnippetUserPart] = old.user.map(user => newApi.SnippetUserPart(user.login, user.update))
    newApi.SnippetId(base64UUID = old.base64UUID, user = newUser)

  def convertSnippet(old: oldStorage.MongoSnippet) =
      import org.scastie.storage.*
      val newCode = if old.inputs.code.contains("import com.olegych") && old.oldId != 0 then
        old.inputs.code.replace("import com.olegych", "import org.scastie")
      else
        old.inputs.code

      val newProgresses = old.progresses.map(p =>
        newApi.SnippetProgress(
          ts = p.ts,
          id = p.id,
          snippetId = p.snippetId.map(convertSnippetId),
          userOutput = p.userOutput.map(o => newApi.ProcessOutput(o.line, tpe = convertType(o.tpe), id = o.id)),
          buildOutput = p.sbtOutput.map(o => newApi.ProcessOutput(o.line, tpe = convertType(o.tpe), id = o.id)),
          compilationInfos = p.compilationInfos.map(problem => newApi.Problem(convertSeverity(problem.severity), problem.line, problem.message)),
          instrumentations = p.instrumentations.map(convertInstrumentation),
          runtimeError = p.runtimeError.map(er => runtimeApi.RuntimeError(er.message, er.line, er.fullStack)),
          scalaJsContent = p.scalaJsContent,
          scalaJsSourceMapContent = p.scalaJsSourceMapContent,
          isDone = p.isDone,
          isTimeout = p.isTimeout,
          isSbtError = p.isSbtError,
          isForcedProgramMode = p.isForcedProgramMode
        )
      )

      val newTarget = convertTarget(old.inputs.target)
      val newInputs: newApi.BaseInputs = newApi.SbtInputs(
        isWorksheetMode = old.inputs.isWorksheetMode,
        code = newCode,
        target = newTarget,
        libraries = old.inputs.libraries.map(l => newApi.ScalaDependency(l.groupId, l.artifact, newTarget, l.version)),
        librariesFromList = convertLibrariesFromList(old.inputs.librariesFromList, newTarget),
        sbtConfigExtra = old.inputs.sbtConfigExtra,
        sbtConfigSaved = old.inputs.sbtConfigSaved,
        sbtPluginsConfigExtra = old.inputs.sbtPluginsConfigExtra,
        sbtPluginsConfigSaved = old.inputs.sbtPluginsConfigSaved,
        isShowingInUserProfile = old.inputs.isShowingInUserProfile,
        forked = old.inputs.forked.map(convertSnippetId)
      )

      Snippet(
        simpleSnippetId = old.simpleSnippetId,
        username = old.user,
        snippetId = convertSnippetId(old.snippetId),
        inputs = newInputs,
        progresses = newProgresses,
        scalaJsContent = old.scalaJsContent,
        scalaJsSourceMapContent = old.scalaJsSourceMapContent,
        time = old.time
      )
  def saveSnippetInNewDatabase(snippets: Seq[Snippet]): Future[Unit] = {
    // Przetwarzaj 10 snippetów równocześnie zamiast wszystkich sekwencyjnie
    Future.traverse(snippets.grouped(10).toSeq) { batch =>
      Future.traverse(batch) { s =>
        for {
          _ <- pgContainer.insertWithExistingId(s.snippetId, s.inputs)
          _ <- Future.sequence(s.progresses.map(pgContainer.appendOutput)) // równolegle zamiast .traverse
        } yield ()
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
        oldSnippets
          .find()
          .skip(count.toInt)
          .limit(1000)
          .toFuture()
          .flatMap { docs =>
            docs.foreach { doc =>
              val sOpt = fromBson[oldStorage.MongoSnippet](doc)
              sOpt match {
                case Some(s) =>
                  if (s.user.isEmpty) println(s"Skipping anonymous snippet ${s.snippetId.url}")
                  else pprint.pprintln(s"Migrating snippet ${s.snippetId.url}")
                case None =>
                  pprint.pprintln(s"Could not deserialize snippet: ${doc.toJson()}")
              }
            }

            val snippets = docs
              .flatMap(fromBson[oldStorage.MongoSnippet])
              .flatMap { s =>
                if (s.user.isEmpty) None
                else Some(convertSnippet(s))
              }
            count += docs.size

            allUrls ++= snippets.map(_.snippetId.url)
            saveSnippetInNewDatabase(snippets).flatMap(_ => step())
          }
      }
    }

    step().map(_ => allUrls.toSeq)
  }

  def verifySnippets(migratedSnippetUrls: Seq[String]): Future[Unit] = {
    pprint.pprintln("Verifying snippets...")

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
          val snippetId = newApi.SnippetId.fromString(url)

          for {
            oldOpt <- {
              oldSnippets
                .find(Filters.eq("simpleSnippetId", url))
                .headOption()
                .map(_.flatMap(fromBson[oldStorage.MongoSnippet]))
            }
            newOpt <- Future.successful(pgContainer.readPostgresSnippet(snippetId))
          } yield (url, oldOpt, newOpt)
        }.flatMap { results =>
          results.foreach { case (url, oldOpt, newOpt) =>
            (oldOpt, newOpt) match {
              case (Some(old), Some(newS)) =>
                val oldCode = old.inputs.code.replace("import com.olegych", "import org.scastie")
                val newCode = newS.inputs match {
                  case sbt: newApi.SbtInputs => sbt.code
                  case cli: newApi.ScalaCliInputs => cli.code
                }

                if (oldCode != newCode) {
                  errors += 1
                  pprint.pprintln(s"❌ Snippet $url mismatch")
                } else {
                  pprint.pprintln(s"✓ Snippet $url verified successfully")
                }

              case (None, Some(_)) =>
                errors += 1
                pprint.pprintln(s"❌ Snippet $url missing in old database")

              case (Some(_), None) =>
                errors += 1
                pprint.pprintln(s"❌ Snippet $url missing in new database")

              case (None, None) =>
                errors += 1
                pprint.pprintln(s"❌ Snippet $url missing in both databases")
            }
          }

          count += batch.size
          val cProgress = (count * 100) / migratedSnippetUrls.size
          if (cProgress > progress) {
            progress = cProgress.toInt
            pprint.pprintln(s"Verification Progress: [$cProgress% / 100%]")
          }

          step()
        }
      }
    }

    step()
  }

  try {
    val snippetsNumber = Await.result(oldSnippets.countDocuments().head, Duration.Inf)

    pprint.pprintln(s"Starting migration of $snippetsNumber snippets...")

    val migrationFuture = for {
      migratedUrls <- migrateSnippets(snippetsNumber.toInt)
      _ <- verifySnippets(migratedUrls)
    } yield ()

    Await.result(migrationFuture, Duration.Inf)
    
  } catch {
    case t: Throwable => t.printStackTrace()
  } finally {
    mongoClient.close()
  }
}