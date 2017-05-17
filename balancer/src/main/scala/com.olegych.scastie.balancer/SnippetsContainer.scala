package com.olegych.scastie
package balancer

import api._

import upickle.default.{write => uwrite, read => uread}

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file._
import FileVisitResult.CONTINUE

import java.util.{Base64, UUID}

import System.{lineSeparator => nl}

case class UserLogin(login: String)

class SnippetsContainer(root: Path, oldRoot: Path) {

  def create(inputs: Inputs, user: Option[UserLogin]): SnippetId = {
    val uuid = randomUrlFirendlyBase64UUID
    val snippetId =
      SnippetId(uuid, user.map(u => SnippetUserPart(u.login, None)))
    write(inputsFile(snippetId), uwrite(inputs))
    snippetId
  }

  def save(inputs: Inputs, user: Option[UserLogin]): SnippetId = {
    create(inputs.copy(showInUserProfile = true), user)
  }

  def fork(snippetId: SnippetId,
           inputs: Inputs,
           user: Option[UserLogin]): Option[SnippetId] = {
    if (readInputs(snippetId).isDefined) {
      Some(
        create(inputs.copy(forked = Some(snippetId), showInUserProfile = true),
               user)
      )
    } else None
  }

  def update(snippetId: SnippetId, inputs: Inputs): Option[SnippetId] = {
    snippetId.user match {
      case Some(SnippetUserPart(login, _)) => {
        val nextSnippetId =
          SnippetId(
            snippetId.base64UUID,
            Some(
              SnippetUserPart(
                login,
                Some(lastUpdateId(login, snippetId.base64UUID) + 1)
              )
            )
          )
        write(inputsFile(nextSnippetId),
              uwrite(inputs.copy(showInUserProfile = true)))
        Some(nextSnippetId)
      }
      case None => None
    }
  }

  def delete(snippetId: SnippetId): Boolean = {
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

  def amend(snippetId: SnippetId, inputs: Inputs): Boolean = {
    if (delete(snippetId)) {
      write(inputsFile(snippetId),
            uwrite(inputs.copy(showInUserProfile = true)))
      true
    } else false
  }

  def appendOutput(progress: SnippetProgress): Unit = {
    (progress.scalaJsContent, progress.scalaJsSourceMapContent,
      progress.snippetId) match {
      case (Some(scalaJsContent), Some(scalaJsSourceMapContent), Some(sid)) => {
        write(scalaJsFile(sid), scalaJsContent)
        write(scalaJsSourceMapFile(sid), scalaJsSourceMapContent)
      }
      case _ => ()
    }

    progress.snippetId.foreach(sid =>
      write(outputsFile(sid), uwrite(progress) + nl, append = true)
    )
  }

  def readSnippet(snippetId: SnippetId): Option[FetchResult] = {
    readInputs(snippetId).map(
      inputs => FetchResult(inputs, readOutputs(snippetId).getOrElse(Nil))
    )
  }

  def readOldSnippet(id: Int): Option[FetchResult] = {
    readOldInputs(id).map(
      inputs => FetchResult(inputs, readOldOutputs(id).getOrElse(Nil))
    )
  }

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

  def readScalaJs(snippetId: SnippetId): Option[FetchResultScalaJs] = {
    slurp(scalaJsFile(snippetId)).map(content => FetchResultScalaJs(content))
  }

  def readScalaJsSourceMap(
      snippetId: SnippetId
  ): Option[FetchResultScalaJsSourceMap] = {
    slurp(scalaJsSourceMapFile(snippetId))
      .map(content => FetchResultScalaJsSourceMap(content))
  }

  def readScalaSource(snippetId: SnippetId): Option[FetchResultScalaSource] = {
    readSnippet(snippetId).flatMap(
      snippet =>
        instrumentation
          .Instrument(snippet.inputs.code, snippet.inputs.target) match {
          case Right(instrumented) =>
            Some(FetchResultScalaSource(instrumented))
          case _ => None
      }
    )
  }

  def listSnippets(user: UserLogin): List[SnippetSummary] = {
    import scala.collection.JavaConverters._
    val dir = root.resolve(user.login)
    if (Files.exists(dir)) {
      val ds = Files.newDirectoryStream(dir)
      val uuids =
        try {
          ds.asScala.map(_.getFileName.toString)
        } catch {
          case util.control.NonFatal(e) => Nil
        } finally {
          ds.close()
        }

      uuids.flatMap { uuid =>
        val updates = updateIdS(user.login, uuid)

        updates.flatMap { update =>
          val snippetId =
            SnippetId(uuid, Some(SnippetUserPart(user.login, Some(update))))
          readInputs(snippetId) match {
            case Some(inputs) =>
              if (inputs.showInUserProfile) {
                List(
                  SnippetSummary(
                    snippetId,
                    inputs.code.split(nl).take(3).mkString(nl)
                  )
                )
              } else Nil
            case None => Nil
          }
        }
      }.toList
    } else Nil
  }

  private def readInputs(snippetId: SnippetId): Option[Inputs] = {
    slurp(inputsFile(snippetId)).map(content => uread[Inputs](content))
  }

  private def readOutputs(
      snippetId: SnippetId
  ): Option[List[SnippetProgress]] = {
    slurp(outputsFile(snippetId)).map(
      _.lines
        .filter(_.nonEmpty)
        .map(line => uread[SnippetProgress](line))
        .toList
    )
  }

  private val inputFileName = "input.json"
  private val outputFileName = "output.json"
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

  private def lastUpdateId(login: String, base64UUID: String): Int = {
    val res = updateIdS(login, base64UUID)
    if (res.isEmpty) 0
    else res.max
  }

  private def updateIdS(login: String, base64UUID: String): List[Int] = {
    val dir = root.resolve(Paths.get(login, base64UUID))
    if (Files.exists(dir)) {
      import scala.collection.JavaConverters._
      val ds = Files.newDirectoryStream(dir)
      try {
        ds.asScala.map(_.getFileName.toString.toInt).toList
      } catch {
        case util.control.NonFatal(e) => Nil
      } finally {
        ds.close()
      }
    } else {
      Nil
    }
  }

  private val anonFolder = "_anonymous_"

  private def rootDir(snippetId: SnippetId): Path = {
    snippetId.user match {
      case Some(SnippetUserPart(login, update)) => {
        root.resolve(login)
      }
      case _ => root.resolve(anonFolder)
    }
  }

  private def snippetFile(snippetId: SnippetId, fileName: String): Path = {
    if (!Files.exists(root)) Files.createDirectory(root)

    val baseDirectory =
      snippetId.user match {
        case Some(SnippetUserPart(login, update)) => {
          val userFolder = root.resolve(login)
          if (!Files.exists(userFolder)) Files.createDirectory(userFolder)

          val base = userFolder.resolve(snippetId.base64UUID)
          if (!Files.exists(base)) Files.createDirectory(base)

          val baseVersion = base.resolve(update.getOrElse(0).toString)
          if (!Files.exists(baseVersion)) Files.createDirectory(baseVersion)

          baseVersion
        }
        case None => {
          val anon = root.resolve(anonFolder)
          if (!Files.exists(anon)) Files.createDirectory(anon)

          val base = anon.resolve(snippetId.base64UUID)
          if (!Files.exists(base)) Files.createDirectory(base)
          base
        }
      }

    baseDirectory.resolve(Paths.get(fileName))
  }

  // example output: GGdknrcEQVu3elXyboKcYQ
  private def randomUrlFirendlyBase64UUID(): String = {
    def toBase64(uuid: UUID): String = {
      val (high, low) =
        (uuid.getMostSignificantBits, uuid.getLeastSignificantBits)
      val buffer = ByteBuffer.allocate(java.lang.Long.BYTES * 2)
      buffer.putLong(high)
      buffer.putLong(low)
      val encoded = Base64.getMimeEncoder.encodeToString(buffer.array())
      encoded.take(encoded.length - 2)
    }

    var res: String = null
    val allowed = ('a' to 'z').toSet ++ ('A' to 'Z').toSet ++ ('0' to '9').toSet

    while (res == null || res.exists(c => !allowed.contains(c))) {
      val uuid = java.util.UUID.randomUUID()
      res = toBase64(uuid)
    }

    res
  }

  private def deleteEmptyDirectories(base: Path): Unit = {
    def dirIsEmpty(dir: Path): Boolean = {
      val ds = Files.newDirectoryStream(dir)
      val ret = ds.iterator().hasNext()
      ds.close()
      !ret
    }

    Files.walkFileTree(
      base,
      new SimpleFileVisitor[Path] {
        override def postVisitDirectory(path: Path,
                                        ex: IOException): FileVisitResult = {
          if (dirIsEmpty(path)) {
            Files.delete(path)
          }
          CONTINUE
        }
      }
    )

    ()
  }
}
