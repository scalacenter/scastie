//> using scala 3
//> using dep org.scastie::api::0.30.0-SNAPSHOT

//> using dep org.scastie:storage_2.13:0.30.0-SNAPSHOT
//> using dep org.scastie.new:storage_2.13:1.0.0-SNAPSHOT
//> using dep org.scastie.new:api_2.13:1.0.0-SNAPSHOT
//> using dep com.typesafe:config:latest.release
//> using dep com.lihaoyi::pprint:latest.release

//> using option -Wunused:all

import com.olegych.scastie.api.AttachedDom
import com.olegych.scastie.api.Html
import com.olegych.scastie.api.ScalaTarget.Js
import com.olegych.scastie.api.ScalaTarget.Scala2
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

@main def migrate =
  val mongoUri = {
    val config       = ConfigFactory.load().getConfig("scastie.mongodb")
    val user         = config.getString("user")
    val password     = config.getString("password")
    val databaseName = config.getString("database")
    val host         = config.getString("host")
    val port         = config.getInt("port")
    s"mongodb://$user:$password@$host:$port/$databaseName"
    // "mongodb://127.0.0.1:27017/?directConnection=true&serverSelectionTimeoutMS=2000&appName=mongosh+1.6.1"
  }

  val client: MongoClient = MongoClient(mongoUri)
  val database: MongoDatabase = client.getDatabase("snippets")
  val snippets = database.getCollection[Document]("snippets")
  val users = database.getCollection[Document]("users")

  val newDatabase: MongoDatabase = client.getDatabase("migratedSnippets")
  val newSnippets = newDatabase.getCollection[Document]("snippets")

  // we write the new json from io.circe decoder
  def toBson[T](obj: T)(using writes: Encoder[T]): Document =
    import io.circe.syntax._
    Document.apply(obj.asJson.noSpaces)

  def fromBson[T](obj: Document)(implicit reads: Reads[T]): Option[T] =
    println(obj.toJson)
    Json.parse(obj.toJson()).asOpt[T]

  def fromBsonCirce[T](obj: Document)(implicit reads: Decoder[T]): Option[T] =
    println(obj.toJson)
    io.circe.parser.decode[T](obj.toJson) match
      case Left(value) =>
        println(value)
        None
      case Right(value) => Some(value)

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
      case Scala2(scalaVersion) => newApi.Scala2(scalaVersion)
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

    MongoSnippet(
      simpleSnippetId = old.simpleSnippetId,
      user = old.user,
      snippetId = convertSnippetId(old.snippetId),
      oldId = old.oldId,
      inputs = newInputs,
      progresses = newProgresses,
      scalaJsContent = old.scalaJsContent,
      scalaJsSourceMapContent = old.scalaJsSourceMapContent,
      time = old.time,
    )

  def saveSnippetInNewDatabase(migratedSnippets: Seq[newStorage.MongoSnippet]) =
    newSnippets.insertMany(migratedSnippets.map(toBson(_))).head()

  def migrateSnippets(originalSize: Int) =

    pprint.pprintln("Finding snippets hard to migrate")

    var count = 0L
    var progress = 0
    def step() =
      snippets
        .find()
        .skip(count.toInt)
        .limit(1000)
        .toFuture()
        .map(
          _.flatMap(fromBson[oldStorage.MongoSnippet])
            .map(s => {
              val cProgress = (count * 100) / originalSize
              count += 1
              if cProgress > progress then
                progress = cProgress.toInt
                pprint.pprintln(s"Progress: [$cProgress%/ 100%]")

              convertSnippet(s)



            })
        )
        .flatMap(saveSnippetInNewDatabase)


    while count < originalSize do
      Await.result(step(), Duration.Inf)



  def addIndexes(snippets: MongoCollection[_]) = {
    pprint.pprintln("Adding indexes")
    pprint.pprintln(Await.result(snippets.listIndexes().head(), Duration.Inf))
    Await.result(snippets.dropIndexes().head(), Duration.Inf)
    Await.result(snippets.createIndex(Indexes.ascending("simpleSnippetId", "oldId"), IndexOptions().unique(true)).head(), Duration.Inf)
    Await.result(Future.sequence(Seq(
      Indexes.hashed("simpleSnippetId"),
      Indexes.hashed("oldId"),
      Indexes.hashed("user"),
      Indexes.hashed("snippetId.user.login"),
      Indexes.hashed("inputs.isShowingInUserProfile"),
      Indexes.hashed("time")
    ).map(snippets.createIndex(_).head())), Duration.Inf)
  }

  def verifySnippets(originalSize: Int) = {
    pprint.pprintln("Verifying snippets decoding")

    var count = 0L
    var progress = 0
    def step() =
      newSnippets
        .find()
        .skip(count.toInt)
        .limit(1000)
        .toFuture()
        .map(
          _.flatMap(fromBsonCirce[newStorage.MongoSnippet])
            .map(s => {
              val cProgress = (count * 100) / originalSize
              println(count)
              count += 1
              if cProgress > progress then
                progress = cProgress.toInt
                pprint.pprintln(s"Progress: [$cProgress%/ 100%]")
            })
        )


    while count < originalSize do
      Await.result(step(), Duration.Inf)
  }


  def initMigrationDatabase() =
    Await.result(newDatabase.listCollectionNames().collect().head().flatMap(colls =>
      colls.find(_ == "snippets") match {
        case Some(users) => Future.successful(null)
        case None => newDatabase.createCollection("snippets").head()
      }
    ), Duration.Inf)

  try {

    val snippetsNumber = Await.result(snippets.countDocuments().head(), Duration.Inf)

    pprint.pprintln("Starting migration")
    pprint.log("Next step", "STEP PROGRESS")
    // if checkSnippetsCollision() then
      Try {
        initMigrationDatabase()
        pprint.log("Next step", "STEP PROGRESS")
        addIndexes(newSnippets)
        pprint.log("Next step", "STEP PROGRESS")
        migrateSnippets(snippetsNumber.toInt)
        pprint.log("Next step", "STEP PROGRESS")
      } recover {
        case t: Throwable => t.printStackTrace()
      }
      val newSnippetsNumber = Await.result(newSnippets.countDocuments().head(), Duration.Inf)
      pprint.log("FINAL RESULT")
      pprint.log(snippetsNumber == newSnippetsNumber)
      verifySnippets(newSnippetsNumber.toInt)

  } finally {
    client.close()
  }
