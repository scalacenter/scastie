package com.olegych.scastie
package balancer

import api._

import upickle.default.{write => uwrite, read => uread}

import java.nio.file._
import java.nio.ByteBuffer
import java.util.{Base64, UUID}

import System.{lineSeparator => nl}

class SnippetsContainer(root: Path) {

  if(!Files.exists(root)) Files.createDirectory(root)

  def writeSnippet(inputs: Inputs, user: Option[String]): SnippetId = {
    val uuid = randomUrlFirendlyBase64UUID
    val snippetId = SnippetId(uuid, user)
    write(inputsFile(snippetId), uwrite(inputs))    
    snippetId
  }

  def appendOutput(progress: SnippetProgress): Unit = {
    write(outputFile(progress.snippetId), uwrite(progress) + nl, append = true)
  }

  def readSnippet(snippetId: SnippetId): Option[Inputs] = {
    if (Files.exists(inputsFile(snippetId))) {
      read(inputsFile(snippetId)).map(content => uread[Inputs](content))
    } else None
  }

  def readOutput(snippetId: SnippetId): Option[List[SnippetProgress]] = {
    if (Files.exists(outputFile(snippetId)))
      read(outputFile(snippetId)).map(
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

  private def outputFile(snippetId: SnippetId): Path = {
    snippetFile(snippetId, output)
  }

  private def snippetFile(snippetId: SnippetId, name: String): Path = {
    val baseDirectory =
      snippetId.user match {
        case Some(user) => {
          val userFolder = root.resolve(user)
          if(!Files.exists(userFolder)) Files.createDirectory(userFolder)
          userFolder
        }
        case None => root
      }

    baseDirectory.resolve(Paths.get(name + "_" + snippetId.base64UUID + json))
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
}
