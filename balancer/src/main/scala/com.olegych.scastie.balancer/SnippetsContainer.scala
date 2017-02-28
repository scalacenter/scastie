package com.olegych.scastie
package balancer

import api._

import upickle.default.{write => uwrite, read => uread}

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file._
import attribute.BasicFileAttributes
import FileVisitResult.CONTINUE

import java.util.{Base64, UUID}

import System.{lineSeparator => nl}

case class UserLogin(login: String)

class SnippetsContainer(root: Path) {

  def create(inputs: Inputs, user: Option[UserLogin]): SnippetId = {
    val uuid = randomUrlFirendlyBase64UUID
    val snippetId = SnippetId(uuid, user.map(u => SnippetUserPart(u.login, None)))
    write(inputsFile(snippetId), uwrite(inputs))
    snippetId
  }

  def save(inputs: Inputs, user: Option[UserLogin]): SnippetId = {
    create(inputs.copy(showInUserProfile = true), user)
  }

  def fork(snippetId: SnippetId, user: Option[UserLogin]): Option[ForkResult] = {
    readInputs(snippetId).map{inputs => 
      val inputs0 = inputs.copy(showInUserProfile = true)
      ForkResult(create(inputs0, user), inputs0)
    }
  }

  def update(snippetId: SnippetId, inputs: Inputs): Option[SnippetId] = {
    snippetId.user match {
      case Some(SnippetUserPart(login, _)) => {
        val nextSnippetId = 
          SnippetId(
            snippetId.base64UUID,
            Some(SnippetUserPart(login, Some(lastUpdateId(login, snippetId.base64UUID) + 1)))
          )
        write(inputsFile(nextSnippetId), uwrite(inputs.copy(showInUserProfile = true)))
        Some(nextSnippetId)
      }
      case None => None
    }
  }

  def delete(snippetId: SnippetId): Boolean = {
    val in = inputsFile(snippetId)
    if(Files.exists(in)) {
      Files.delete(in)

      val out = outputsFile(snippetId)
      if(Files.exists(out)) {
        Files.delete(out)
      }

      deleteEmptyDirectories(rootDir(snippetId))

      true
    } else false
  }

  def amend(snippetId: SnippetId, inputs: Inputs): Boolean = {
    if(delete(snippetId)) {
      write(inputsFile(snippetId), uwrite(inputs))
      true
    } else false
  }

  def appendOutput(progress: SnippetProgress): Unit = {
    write(outputsFile(progress.snippetId), uwrite(progress) + nl, append = true)
  }

  def readSnippet(snippetId: SnippetId): Option[FetchResult] = {
    readInputs(snippetId).map(inputs =>
      FetchResult(inputs, readOutputs(snippetId).getOrElse(Nil))
    )
  }

  def listSnippets(user: UserLogin): List[SnippetSummary] = {
    import scala.collection.JavaConverters._
    val dir = root.resolve(user.login)
    if(Files.exists(dir)) {

      val ds = Files.newDirectoryStream(dir)

      val uuids =
        try {
          ds.asScala.map(_.getFileName.toString)
        } catch {
          case util.control.NonFatal(e) => Nil
        } finally {
          ds.close()
        }

      uuids.flatMap{uuid =>
        val last = lastUpdateId(user.login, uuid)
        val last0 = if(last == 0) None else Some(last)

        val snippetId = SnippetId(uuid, Some(SnippetUserPart(user.login, last0)))

        val inputs = readInputs(snippetId)

        if(inputs.map(_.showInUserProfile).getOrElse(false)) {
          List(
            SnippetSummary(
              snippetId,
              inputs.map(_.code.split(nl).take(3).mkString(nl)).getOrElse("")  
            )
          )
        } else Nil
      }.toList
    } else Nil
  }

  private def readInputs(snippetId: SnippetId): Option[Inputs] = {
    if (Files.exists(inputsFile(snippetId))) {
      read(inputsFile(snippetId)).map(content => uread[Inputs](content))
    } else None
  }

  private def readOutputs(snippetId: SnippetId): Option[List[SnippetProgress]] = {
    if (Files.exists(outputsFile(snippetId)))
      read(outputsFile(snippetId)).map(
        _.lines
          .filter(_.nonEmpty)
          .map(line => uread[SnippetProgress](line))
          .toList)
    else None
  }

  private val json = ".json"
  private val input = "input"
  private val output = "output"

  private def inputsFile(snippetId: SnippetId): Path = {
    snippetFile(snippetId, input)
  }

  private def outputsFile(snippetId: SnippetId): Path = {
    snippetFile(snippetId, output)
  }

  private def lastUpdateId(login: String, base64UUID: String): Int = {
    val dir = root.resolve(Paths.get(login, base64UUID))
    if(Files.exists(dir)) {
      import scala.collection.JavaConverters._
      val ds = Files.newDirectoryStream(dir)
      try {
        ds.asScala.map(_.getFileName.toString.toInt).max
      } catch {
        case util.control.NonFatal(e) => 0
      } finally {
        ds.close()
      }
    } else {
      0
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

  private def snippetFile(snippetId: SnippetId, name: String): Path = {
    if(!Files.exists(root)) Files.createDirectory(root)
    
    val baseDirectory =
      snippetId.user match {
        case Some(SnippetUserPart(login, update)) => {
          val userFolder = root.resolve(login)
          if(!Files.exists(userFolder)) Files.createDirectory(userFolder)

          val base = userFolder.resolve(snippetId.base64UUID)
          if(!Files.exists(base)) Files.createDirectory(base)
          
          val baseVersion = base.resolve(update.getOrElse(0).toString)
          if(!Files.exists(baseVersion)) Files.createDirectory(baseVersion)
          
          baseVersion
        }
        case None => {
          val anon = root.resolve(anonFolder)
          if(!Files.exists(anon)) Files.createDirectory(anon)

          val base = anon.resolve(snippetId.base64UUID)
          if(!Files.exists(base)) Files.createDirectory(base)
          base
        }
      }

    baseDirectory.resolve(Paths.get(name + json))
  }

  // example output: GGdknrcEQVu3elXyboKcYQ
  private def randomUrlFirendlyBase64UUID(): String = {
    def toBase64(uuid: UUID): String = {
      val (high, low) = (uuid.getMostSignificantBits, uuid.getLeastSignificantBits)
      val buffer = ByteBuffer.allocate(java.lang.Long.BYTES * 2)
      buffer.putLong(high)
      buffer.putLong(low)
      val encoded = Base64.getMimeEncoder.encodeToString(buffer.array())
      encoded.take(encoded.length - 2)
    }

    var res: String = null
    val allowed = ('a' to 'z').toSet ++ ('A' to 'Z').toSet ++ ('0' to '9').toSet
    
    while(res == null || res.exists(c => !allowed.contains(c))) {
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

    Files.walkFileTree(base, new FileVisitor[Path]{ 
      def postVisitDirectory(path: Path, ex: IOException): FileVisitResult = {
        if(dirIsEmpty(path)) {
          Files.delete(path)
        }
        CONTINUE
      }
      def preVisitDirectory(path: Path,x$2: BasicFileAttributes): FileVisitResult = CONTINUE
      def visitFile(path: Path,x$2: BasicFileAttributes): FileVisitResult = CONTINUE
      def visitFileFailed(path: Path, ex: IOException): FileVisitResult = CONTINUE
    })

    ()
  }
}
