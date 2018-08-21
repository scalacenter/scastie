package com.olegych.scastie.storage

import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import com.olegych.scastie.api._

import play.api.libs.json._
import reactivemongo.play.json._
import reactivemongo.play.json.collection._

import reactivemongo.api._
import reactivemongo.api.indexes._
import reactivemongo.bson._

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.util.control.NonFatal

import scala.collection.JavaConverters._

object ImportToMongoDB {

  import akka.actor.ActorSystem
  implicit val system: ActorSystem = ActorSystem("to-mongo")
  import system.dispatcher

  val mongoUri = "mongodb://localhost:27017/snippets"
  val driver = MongoDriver()
  val parsedUri = MongoConnection.parseURI(mongoUri)
  val connection = parsedUri.map(driver.connection)
  val futureConnection = Future.fromTry(connection)
  val fdb = futureConnection.flatMap(_.database("snippets"))
  val snippets = fdb.map(_.collection("snippets"))

  def createIndex(): Future[Unit] = {
    val snippetIdIndex = Index(key = Seq(("simpleSnippetId", IndexType.Hashed)),
                               name = Some("snippets-id"))
    val oldSnippetIdIndex = Index(key = Seq(("oldId", IndexType.Hashed)),
                                  name = Some("snippets-old-id"))

    for {
      col <- snippets
      _ <- col.indexesManager.ensure(snippetIdIndex)
      _ <- col.indexesManager.ensure(oldSnippetIdIndex)
    } yield ()
  }

  def findLast(collection: JSONCollection): Future[Long] = {
    collection
      .find(Json.obj())
      .options(QueryOpts().batchSize(1))
      .sort(Json.obj("time" -> -1))
      .one[BSONDocument]
      .map(
        _.flatMap(
          doc =>
            doc.getAs[Long]("time") orElse doc.getAs[Int]("time").map(_.toLong)
        ).getOrElse(0L)
      )
  }

  def dirSize(path: Path): Int = {
    val d = Files.newDirectoryStream(path)
    val s = d.asScala.size
    d.close()
    s
  }

  def main(args: Array[String]): Unit = {

    Await.result(createIndex(), Duration.Inf)

    val lastTime = Await.result(snippets.flatMap(findLast), Duration.Inf)
    println("last migration: " + lastTime)

    val List(dirPath, dirPath2, oldDirPath) =
      if (args.isEmpty)
        List(
          "/home/gui/scastie-dump2/snippets",
          "/home/gui/scastie-dump2/snippets-snap",
          "/home/gui/scastie-dump2/old-snippets"
        )
      else args.toList

    if (lastTime == 0L) {
      println(s"---- $dirPath -----")
      runNew(dirPath, lastTime)
    }

    println(s"---- $dirPath2 -----")
    runNew(dirPath2, lastTime)

    if (lastTime == 0L) {
      println(s"---- $oldDirPath -----")
      runOld(oldDirPath)
    }

    println("-------")
    println("closing")
    println("-------")
    driver.close(30.seconds)
    system.terminate()
  }

  def runNew(dirPath: String, lastTime: Long): Unit = {
    val dir = Paths.get(dirPath)

    val anonDirName = "_anonymous_"

    if (Files.exists(dir)) {
      println("*** STEP 1: convert users snippets ***")

      // level 0: /snippets
      val ds = Files.newDirectoryStream(dir)
      val toInsert =
        ds.asScala.iterator
          .filter(_.getFileName.toString != anonDirName)
          .flatMap(doUser(lastTime))

      var total = 0
      toInsert.foreach { elem =>
        Await.result(insertOne(elem), Duration.Inf)
        total += 1
        if (total % 100 == 0) {
          println(total)
        }
      }

      ds.close()

      val anon = dir.resolve(anonDirName)
      if (Files.exists(anon)) {
        println("*** STEP 2: convert annon snippets ***")
        doAnon(anon, lastTime)
      }
    }
  }

  def runOld(oldDirPath: String): Unit = {
    val old = Paths.get(oldDirPath)

    println("*** STEP 3: convert old scastie.org snippets ***")
    if (Files.exists(old)) {
      val ds = Files.newDirectoryStream(old)

      val toInsert = ds.asScala.map(doOld).flatten

      var total = 0
      toInsert.foreach { elem =>
        Await.result(insertOne(elem), Duration.Inf)
        total += 1
        if (total % 100 == 0) {
          println(total)
        }
      }

      ds.close()
    }
  }

  // level 1: /snippets/_anonymous_
  def doAnon(anonDir: Path, lastTime: Long): Unit = {
    val ds = Files.newDirectoryStream(anonDir)
    val toInsert = ds.asScala.iterator.flatMap(doBase64Anon(lastTime))
    var total = 0
    toInsert.foreach { elem =>
      Await.result(insertOne(elem), Duration.Inf)
      total += 1
      if (total % 100 == 0) {
        println(total)
      }
    }
    ds.close()
  }

  // level 2: /snippets/_anonymous_/fYC3KzfuTvSgejKtMcNYrQ
  def doBase64Anon(lastTime: Long)(base64Dir: Path): Option[MongoSnippet] = {
    val snippetId = SnippetId(base64Dir.getFileName.toString, None)
    toMongoSnippet(snippetId, base64Dir, lastTime)
  }

  // level 1: /snippets/MasseGuillaume
  def doUser(lastTime: Long)(userDir: Path): List[MongoSnippet] = {
    val ds = Files.newDirectoryStream(userDir)
    val out = ds.asScala.toList.flatMap(doBase64(lastTime))
    ds.close()
    out
  }

  // level 2: /snippets/MasseGuillaume/fYC3KzfuTvSgejKtMcNYrQ
  def doBase64(lastTime: Long)(base64Dir: Path): List[MongoSnippet] = {
    if (isDir(base64Dir)) {
      val ds = Files.newDirectoryStream(base64Dir)
      val out = ds.asScala.flatMap(doUpdate(lastTime)).toList
      ds.close()
      out
    } else {
      Nil
    }
  }

  // level 3: /snippets/MasseGuillaume/fYC3KzfuTvSgejKtMcNYrQ/0
  def doUpdate(lastTime: Long)(updateDir: Path): Option[MongoSnippet] = {
    val count = updateDir.getNameCount
    val user = updateDir.getName(count - 3).toString
    val base64 = updateDir.getName(count - 2).toString
    val update = updateDir.getName(count - 1).toString

    try {
      val snippetId =
        SnippetId(base64, Some(SnippetUserPart(user, update.toInt)))
      toMongoSnippet(snippetId, updateDir, lastTime)
    } catch {
      case _: java.lang.NumberFormatException => None
    }
  }

  // /snippets-old
  //   /paste00000000000000000001
  //     /src/main/scala
  //       test.scala
  //       output.txt

  def doOld(idDir: Path): Option[MongoSnippet] = {
    val id = idDir.getFileName.toString.drop("paste".length).toLong

    val content = idDir.resolve("src/main/scala")

    val input = slurp(content.resolve("test.scala"))
      .map(OldScastieConverter.convertOldInput)
    val output = slurp(content.resolve("output.txt"))
      .map(OldScastieConverter.convertOldOutput)

    (input, output) match {
      case (Some(in), Some(out)) => {
        val snippetId = SnippetId("old", None)
        Some(
          MongoSnippet(
            simpleSnippetId = snippetId.url,
            user = None,
            snippetId = snippetId,
            oldId = id,
            inputs = in,
            progresses = out,
            scalaJsContent = "",
            scalaJsSourceMapContent = "",
            time = 0
          )
        )
      }
      case _ => {
        println(
          s"cannot read: input: ${input.isEmpty}, output: ${output.isEmpty}"
        )
        None
      }
    }

  }

  def toMongoSnippet(snippetId: SnippetId,
                     dir: Path,
                     lastTime: Long): Option[MongoSnippet] = {
    getInputs(dir, lastTime).flatMap(
      in =>
        if (in.isShowingInUserProfile) {
          Some(
            MongoSnippet(
              simpleSnippetId = snippetId.url,
              user = None,
              snippetId = snippetId,
              oldId = 0,
              inputs = in,
              progresses = getProgress(dir),
              scalaJsContent = getScalaJsContent(dir),
              scalaJsSourceMapContent = getScalaJsSourceMapContent(dir),
              time = getTime(dir)
            )
          )
        } else
        None
    )
  }

  def slurp(path: Path): Option[String] =
    if (Files.exists(path)) Some(new String(Files.readAllBytes(path)))
    else None

  def inputFile(dir: Path): Path = dir.resolve("input3.json")
  def getInputs(dir: Path, lastTime: Long): Option[Inputs] = {
    slurp(inputFile(dir)).flatMap { json =>
      val time = getTime(dir)
      if (time > lastTime) {
        try {
          Json.fromJson[Inputs](Json.parse(json)).asOpt
        } catch {
          case NonFatal(e) => None
        }
      } else None
    }
  }
  def getTime(dir: Path): Long = getFileTimestamp(inputFile(dir))

  def getProgress(dir: Path): List[SnippetProgress] = {
    val maxProgress = 100

    slurp(dir.resolve("output3.json"))
      .map(
        _.lines
          .filter(_.nonEmpty)
          .toList
          .takeRight(maxProgress)
          .map(
            line =>
              try {
                Json.fromJson[SnippetProgress](Json.parse(line)).asOpt
              } catch {
                case NonFatal(e) =>
                  e.printStackTrace()
                  None
            }
          )
          .flatten
      )
      .getOrElse(Nil)
  }

  def getScalaJsContent(dir: Path): String =
    slurp(dir.resolve("fastopt.js")).getOrElse("")

  def getScalaJsSourceMapContent(dir: Path): String =
    slurp(dir.resolve("fastopt.js.map")).getOrElse("")

  private def getFileTimestamp(filePath: Path): Long = {
    val attr = Files.readAttributes(filePath, classOf[BasicFileAttributes])
    attr.creationTime().toMillis
  }

  def isDir(base64Dir: Path): Boolean =
    Files.isDirectory(base64Dir) &&
      !base64Dir.getFileName.toString.startsWith("project_")

  def insertOne(snippetsToInsert: MongoSnippet): Future[Unit] = {
    snippets
      .flatMap(_.insert[MongoSnippet](ordered = false).one(snippetsToInsert))
      .map(
        wr =>
          if (!wr.ok) {
            println("failed to write to mongodb\n" + wr)
        }
      )
  }
}
