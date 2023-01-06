package com.olegych.scastie.web.oauth2

import java.lang.System.{lineSeparator => nl}
import java.nio.file._
import java.util.UUID

import akka.actor.ActorSystem
import com.olegych.scastie.api.User
import com.softwaremill.session._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import play.api.libs.json.Json

import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.NonFatal

class GithubUserSession(system: ActorSystem) {
  val logger = Logger("GithubUserSession")

  private val configuration =
    ConfigFactory.load().getConfig("com.olegych.scastie.web")
  private val usersFile =
    Paths.get(configuration.getString("oauth2.users-file"))
  private val usersSessions =
    Paths.get(configuration.getString("oauth2.sessions-file"))

  private val sessionConfig =
    SessionConfig.default(configuration.getString("session-secret"))

  private lazy val users = {
    val trie = TrieMap[UUID, User]()
    trie ++= readSessionsFile()
    trie
  }

  implicit def serializer: SessionSerializer[UUID, String] =
    new SingleValueSessionSerializer(
      _.toString(),
      (id: String) => Try { UUID.fromString(id) }
    )
  implicit val sessionManager = new SessionManager[UUID](sessionConfig)
  implicit val refreshTokenStorage = new ActorRefreshTokenStorage(system)

  private def readSessionsFile(): Vector[(UUID, User)] = {
    if (Files.exists(usersSessions)) {
      val content = Files.readAllLines(usersSessions).toArray.mkString(nl)
      try {
        Json
          .fromJson[Vector[(UUID, User)]](Json.parse(content))
          .asOpt
          .getOrElse(Vector())
      } catch {
        case NonFatal(e) =>
          logger.error("failed to read sessions", e)
          Vector()
      }
    } else {
      Vector()
    }
  }

  private def appendSessionsFile(uuid: UUID, user: User): Unit = synchronized {
    val pair = uuid -> user
    users += pair
    val sessions = readSessionsFile()
    val sessions0 = sessions :+ pair

    if (Files.exists(usersSessions)) {
      Files.delete(usersSessions)
    }

    Files.write(
      usersSessions,
      Json.prettyPrint(Json.toJson(sessions0)).getBytes,
      StandardOpenOption.CREATE
    )

    ()
  }

  def addUser(user: User): UUID = {
    val uuid = UUID.randomUUID
    appendSessionsFile(uuid, user)
    storeUser(user.login)
    uuid
  }

  def storeUser(login: String): Unit = {
    val lines =
      if (Files.exists(usersFile)) Files.readAllLines(usersFile).asScala
      else Seq()

    if (!lines.contains(login)) {
      Files.write(usersFile, (login + nl).getBytes, StandardOpenOption.APPEND, StandardOpenOption.CREATE)
      ()
    }
  }

  def getUser(id: Option[UUID]): Option[User] =
    id.flatMap(users.get)
}
