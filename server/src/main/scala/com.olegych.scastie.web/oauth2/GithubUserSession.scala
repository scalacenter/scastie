package com.olegych.scastie
package web
package oauth2

import com.softwaremill.session._

import com.typesafe.config.ConfigFactory

import upickle.default.{ReadWriter, write => uwrite, read => uread}

import scala.collection.parallel.mutable.ParTrieMap
import scala.concurrent.ExecutionContext

import com.typesafe.scalalogging.Logger

import scala.util.Try
import java.util.UUID
import java.nio.file._
// import util.Properties

import System.{lineSeparator => nl}

class GithubUserSession()(implicit val executionContext: ExecutionContext) {
  val logger = Logger("GithubUserSession")

  private val configuration =
    ConfigFactory.load().getConfig("com.olegych.scastie.web")
  private val usersFile =
    Paths.get(configuration.getString("oauth2.users-file"))
  private val usersSessions =
    Paths.get(configuration.getString("oauth2.sessions-file"))

  private val sessionConfig =
    SessionConfig.default(configuration.getString("sesssion-secret"))

  private lazy val users = {
    val trie = ParTrieMap[UUID, User]()
    readSessionsFile().map {
      case (uuid, user) =>
        val pair = uuid -> user
        trie += pair
    }
    trie
  }

  import upickle.Js
  private implicit val pkl: ReadWriter[UUID] =
    ReadWriter[UUID](
      uuid => Js.Str(uuid.toString),
      { case Js.Str(rawUUID) => UUID.fromString(rawUUID) }
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
      else logger.info(msg)
  }

  private def readSessionsFile(): Array[(UUID, User)] = {
    if (Files.exists(usersSessions)) {
      val content = Files
        .readAllLines(usersSessions)
        .toArray
        .mkString(nl)
      uread[Array[(UUID, User)]](content)
    } else Array()
  }

  def appendSessionsFile(uuid: UUID, user: User): Unit = {
    val pair = uuid -> user
    users += pair
    val sessions = readSessionsFile()
    val sessions0 = sessions :+ pair

    if (Files.exists(usersSessions)) {
      Files.delete(usersSessions)
    }

    Files.write(
      usersSessions,
      uwrite(sessions0).getBytes,
      StandardOpenOption.CREATE
    )

    ()
  }

  def addUser(user: User): UUID = {
    val uuid = UUID.randomUUID
    appendSessionsFile(uuid, user)
    addBetaUser(user.login)
    uuid
  }

  def addBetaUser(login: String): Unit = {
    if (!exists(login)) {
      Files.write(usersFile,
                  (login + nl).getBytes,
                  StandardOpenOption.APPEND,
                  StandardOpenOption.CREATE)
      ()
    }
  }

  def exists(login: String): Boolean = {
    if (Files.exists(usersFile)) {
      Files.readAllLines(usersFile).toArray.contains(login)
    } else false
  }

  def getUser(id: Option[UUID]): Option[User] =
    id.flatMap(users.get)
}
