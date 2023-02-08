package com.olegych.scastie.storage.filesystem

import com.olegych.scastie.api._
import com.olegych.scastie.storage.OldScastieConverter
import com.olegych.scastie.storage.SnippetsContainer
import com.olegych.scastie.storage.UserLogin
import play.api.libs.json.Json

import java.io.IOException
import java.nio.file._
import scala.concurrent.Future

import System.{lineSeparator => nl}


trait FilesystemSnippetsContainer extends SnippetsContainer with GenericFilesystemContainer {
  val root: Path
  val oldRoot: Path

  def appendOutput(progress: SnippetProgress): Future[Unit] = Future {
    (progress.scalaJsContent, progress.scalaJsSourceMapContent, progress.snippetId) match {
      case (Some(scalaJsContent), Some(scalaJsSourceMapContent), Some(sid)) =>
        write(scalaJsFile(sid), scalaJsContent)
        write(scalaJsSourceMapFile(sid), scalaJsSourceMapContent)
      case _ => ()
    }

    progress.snippetId.foreach(
      sid => append(outputsFile(sid), Json.stringify(Json.toJson(progress)) + nl)
    )
  }

  def delete(snippetId: SnippetId): Future[Boolean] = {
    def rootDir(snippetId: SnippetId): Path = {
      snippetId.user match {
        case Some(SnippetUserPart(login, _)) =>
          root.resolve(login)
        case _ => root.resolve(anonFolder)
      }
    }

    Future {
      val in = inputsFile(snippetId)
      if (Files.exists(in)) {
        Files.delete(in)

        val out = outputsFile(snippetId)
        if (Files.exists(out)) {
          Files.delete(out)
        }

        deleteEmptyDirectories(rootDir(snippetId))

        true
      } else false
    }
  }

  def listSnippets(user: UserLogin): Future[List[SnippetSummary]] = {
    def updateIdS(login: String, base64UUID: String): List[Int] = {
      val dir = root.resolve(Paths.get(login, base64UUID))
      if (Files.isDirectory(dir)) {
        import scala.jdk.CollectionConverters._
        val ds = Files.newDirectoryStream(dir)
        try {
          ds.asScala.map(_.getFileName.toString.toInt).toList
        } catch {
          case util.control.NonFatal(_) => Nil
        } finally {
          ds.close()
        }
      } else {
        Nil
      }
    }

    def getFileTimestamp(snippetId: SnippetId): Long = {
      import java.nio.file.Files
      import java.nio.file.attribute.BasicFileAttributes

      val filePath = inputsFile(snippetId)
      val attr = Files.readAttributes(filePath, classOf[BasicFileAttributes])

      attr.creationTime().toMillis
    }

    Future {
      import scala.jdk.CollectionConverters._
      val dir = root.resolve(user.login)
      if (Files.exists(dir)) {
        val ds = Files.newDirectoryStream(dir)
        val uuids =
          try {
            ds.asScala.map(_.getFileName.toString)
          } catch {
            case util.control.NonFatal(_) => Nil
          } finally {
            ds.close()
          }

        uuids
          .flatMap { uuid =>
            val updates = updateIdS(user.login, uuid)

            updates
              .flatMap { update =>
                val snippetId =
                  SnippetId(uuid, Some(SnippetUserPart(user.login, update)))
                readInputs(snippetId) match {
                  case Some(inputs) =>
                    if (inputs.isShowingInUserProfile) {
                      List(
                        SnippetSummary(
                          snippetId,
                          inputs.code.split(nl).take(3).mkString(nl),
                          getFileTimestamp(snippetId)
                        )
                      )
                    } else Nil
                  case None => Nil
                }
              }
          }
          .toList
          .sortBy(-_.time)
      } else Nil
    }
  }

  def readOldSnippet(id: Int): Future[Option[FetchResult]] = {

    def oldPath(id: Int): Path =
      oldRoot
        .resolve("paste%20d".format(id).replaceAll(" ", "0"))
        .resolve("src/main/scala/")

    def readOldInputs(id: Int): Option[Inputs] = {
      slurp(oldPath(id).resolve("test.scala"))
        .map(OldScastieConverter.convertOldInput)
    }

    def readOldOutputs(id: Int): Option[List[SnippetProgress]] = {
      slurp(oldPath(id).resolve("output.txt"))
        .map(OldScastieConverter.convertOldOutput)
    }

    Future {
      readOldInputs(id).map(
        inputs => FetchResult.create(inputs, readOldOutputs(id).getOrElse(Nil))
      )
    }
  }

  def readScalaJs(snippetId: SnippetId): Future[Option[FetchResultScalaJs]] =
    Future {
      slurp(scalaJsFile(snippetId)).map(content => FetchResultScalaJs(content))
    }

  def readScalaJsSourceMap(
      snippetId: SnippetId
  ): Future[Option[FetchResultScalaJsSourceMap]] = Future {
    slurp(scalaJsSourceMapFile(snippetId))
      .map(content => FetchResultScalaJsSourceMap(content))
  }

  def readSnippet(snippetId: SnippetId): Future[Option[FetchResult]] = Future {
    readInputs(snippetId).map(
      inputs => FetchResult.create(inputs, readOutputs(snippetId).getOrElse(Nil))
    )
  }

  protected def insert(snippetId: SnippetId, inputs: Inputs): Future[Unit] =
    Future {
      write(inputsFile(snippetId), Json.prettyPrint(Json.toJson(inputs.withSavedConfig)))
    }

  override protected def hideFromUserProfile(snippetId: SnippetId): Future[Unit] =
    for {
      old <- readSnippet(snippetId)
      _ <- Future.traverse(old.toList) { old =>
        insert(snippetId, old.inputs.copy(isShowingInUserProfile = false))
      }
    } yield ()

  private val anonFolder = "_anonymous_"
  private val inputFileName = "input3.json"
  private val outputFileName = "output3.json"
  private val scalaJsFileName = ScalaTarget.Js.targetFilename
  private val scalaJsSourceMapFileName = ScalaTarget.Js.sourceMapFilename

  private def inputsFile(snippetId: SnippetId): Path = {
    snippetFile(snippetId, inputFileName)
  }

  private def outputsFile(snippetId: SnippetId): Path = {
    snippetFile(snippetId, outputFileName)
  }

  private def scalaJsFile(snippetId: SnippetId): Path = {
    snippetFile(snippetId, scalaJsFileName)
  }

  private def scalaJsSourceMapFile(snippetId: SnippetId): Path = {
    snippetFile(snippetId, scalaJsSourceMapFileName)
  }

  private def snippetFile(snippetId: SnippetId, fileName: String): Path = {
    if (!Files.exists(root)) Files.createDirectory(root)

    val baseDirectory =
      snippetId.user match {
        case Some(SnippetUserPart(login, update)) =>
          val userFolder = root.resolve(login)
          if (!Files.exists(userFolder)) Files.createDirectory(userFolder)

          val base = userFolder.resolve(snippetId.base64UUID)
          if (!Files.exists(base)) Files.createDirectory(base)

          val baseVersion = base.resolve(update.toString)
          if (!Files.exists(baseVersion)) Files.createDirectory(baseVersion)

          baseVersion
        case None =>
          val anon = root.resolve(anonFolder)
          if (!Files.exists(anon)) Files.createDirectory(anon)

          val base = anon.resolve(snippetId.base64UUID)
          if (!Files.exists(base)) Files.createDirectory(base)
          base
      }

    baseDirectory.resolve(Paths.get(fileName))
  }

  private def readInputs(snippetId: SnippetId): Option[Inputs] = {
    slurp(inputsFile(snippetId))
      .map(
        content =>
          Json
            .fromJson[Inputs](Json.parse(content))
            .fold(e => sys.error(e.toString + s" for ${snippetId} $content"), identity)
      )
  }

  private def readOutputs(
      snippetId: SnippetId
  ): Option[List[SnippetProgress]] = {
    slurp(outputsFile(snippetId)).map {
      _.linesIterator
        .filter(_.nonEmpty)
        .map { line =>
          Json
            .fromJson[SnippetProgress](Json.parse(line))
            .fold(e => sys.error(e.toString + s" for ${snippetId} $line"), identity)
        }
        .toList
    }
  }


  private def deleteEmptyDirectories(base: Path): Unit = {
    def dirIsEmpty(dir: Path): Boolean = {
      val ds = Files.newDirectoryStream(dir)
      val ret = ds.iterator().hasNext
      ds.close()
      !ret
    }

    Files.walkFileTree(
      base,
      new SimpleFileVisitor[Path] {
        override def postVisitDirectory(path: Path, ex: IOException): FileVisitResult = {
          if (dirIsEmpty(path)) {
            Files.delete(path)
          }
          FileVisitResult.CONTINUE
        }
      }
    )

    ()
  }
}
