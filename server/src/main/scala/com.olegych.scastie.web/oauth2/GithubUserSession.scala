package com.olegych.scastie
package web
package oauth2

import com.typesafe.config.ConfigFactory

import com.softwaremill.session._

import upickle.default.ReadWriter
import upickle.default.{Reader, Writer, write => uwrite, read => uread}


import scala.collection.parallel.mutable.ParTrieMap
import scala.concurrent.ExecutionContext

import scala.util.Try
import java.util.UUID
import java.nio.file._
import util.Properties

class GithubUserSession()(implicit val executionContext: ExecutionContext) {

  private val config = ConfigFactory.load().getConfig("com.olegych.scastie.web.oauth2")
  private val usersFile = Paths.get(config.getString("users-file").get)
  private val usersSessions = Paths.get(config.getString("sessions-file").get)

  private val sessionConfig = SessionConfig.default(config.getString("sesssion-secret"))

  private lazy val users = {
    val trie = ParTrieMap[UUID, User]()
    readSessionsFile().map{ case (uuid, user) =>
      val pair = uuid -> user
      trie += pair
    }
    trie
  }

  import upickle.Js
  private implicit val pkl: ReadWriter[UUID] = 
    ReadWriter[UUID](
      uuid => Js.Str(uuid.toString), 
      { case Js.Str(rawUUID) => UUID.fromString(rawUUID)}
    )

  implicit def serializer: SessionSerializer[UUID, String] =
    new SingleValueSessionSerializer(
      _.toString(),
      (id: String) => Try { UUID.fromString(id) }
    )
  implicit val sessionManager = new SessionManager[UUID](sessionConfig)
  implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[UUID] {
    def log(msg: String) =
      if (msg.startsWith("Looking up token for selector")) () // borring
      else println(msg)
  }

  private def readSessionsFile(): Array[(UUID, User)] = {
    if(Files.exists(usersSessions)) {
      val content = Files.readAllLines(usersSessions).toArray.mkString(System.lineSeparator)
      uread[Array[(UUID, User)]](content)
    } else Array()
  }

  def appendSessionsFile(uuid: UUID, user: User): Unit = {
    val pair = uuid -> user
    users += pair
    val sessions = readSessions()
    val sessions0 = sessions :+ pair

    if(Files.exists(usersSessions)) {
      Files.delete(usersSessions)
    }

    Files.write(
      usersSessions,
      uwrite(sessions0).getBytes,
      StandardOpenOption.CREATE
    )

    ()
  }

  def storeSession(user: User): UUID = {
    val uuid = UUID.randomUUID
    appendSession(uuid, user)
    uuid
  }

  def get(uuid: UUID): Option[User] = {
    users.get(uuid)
  }

  def add(login: String): Unit = {
    if(!exists(login)) {
      Files.write(usersFile, login.getBytes, StandardOpenOption.APPEND, StandardOpenOption.CREATE)
      ()
    }
  }

  def exists(login: String): Boolean = {
    if (Files.exists(usersFile)) {
      Files.readAllLines(usersFile).toArray.contains(login)
    }
    else false
  }

  def getUser(id: Option[UUID]): Option[User] = id.flatMap(users.get)
}
