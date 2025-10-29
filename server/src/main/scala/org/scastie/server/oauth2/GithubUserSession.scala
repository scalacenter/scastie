package org.scastie.web.oauth2

import java.lang.System.{lineSeparator => nl}
import java.nio.file._
import java.util.UUID

import akka.actor.ActorSystem
import org.scastie.api.User
import org.scastie.api.UserData
import com.softwaremill.session._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import io.circe.syntax._
import io.circe.parser._

import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.NonFatal

class GithubUserSession(system: ActorSystem) {
  val logger = Logger("GithubUserSession")

  private val configuration =
    ConfigFactory.load().getConfig("org.scastie.web")
  private val usersFile =
    Paths.get(configuration.getString("oauth2.users-file"))
  private val usersSessions =
    Paths.get(configuration.getString("oauth2.sessions-file"))

  private val sessionConfig =
    SessionConfig.default(configuration.getString("session-secret"))

  private lazy val usersData: TrieMap[UUID, (User, List[User])] = {
    val trie = TrieMap[UUID, (User, List[User])]()
    trie ++= readSessionsFile().map { case (uuid, user, switchableUsers) => uuid -> (user, switchableUsers) }
    trie
  }

  implicit def serializer: SessionSerializer[UUID, String] =
    new SingleValueSessionSerializer(
      _.toString(),
      (id: String) => Try { UUID.fromString(id) }
    )
  implicit val sessionManager: SessionManager[UUID] = new SessionManager[UUID](sessionConfig)
  implicit val refreshTokenStorage: ActorRefreshTokenStorage = new ActorRefreshTokenStorage(system)

  private def generateUniqueUUID(): UUID = {
    val uuid = UUID.randomUUID
    if (usersData.contains(uuid)) generateUniqueUUID()
    else uuid
  }

  private def readSessionsFile(): Vector[(UUID, User, List[User])] = {
    if (Files.exists(usersSessions)) {
      val content = Files.readAllLines(usersSessions).toArray.mkString(nl)
      try {
        decode[Vector[(UUID, User, List[User])]](content).toOption.getOrElse{
          decode[Vector[(UUID, User)]](content).toOption
            .map(_.map { case (uuid, user) => (uuid, user, List.empty[User]) })
            .getOrElse(Vector())
        }
      } catch {
        case NonFatal(e) =>
          logger.error("failed to read sessions", e)
          Vector()
      }
    } else {
      Vector()
    }
  }

  private def appendSessionsFile(uuid: UUID, user: User, switchableUsers: List[User]): Unit = synchronized {
    val pair = uuid -> (user, switchableUsers)
    usersData += pair
    val sessions = readSessionsFile()
    val sessions0 = sessions :+ (uuid, user, switchableUsers)

    if (Files.exists(usersSessions)) {
      Files.delete(usersSessions)
    }

    Files.write(
      usersSessions,
      sessions0.asJson.spaces2.getBytes,
      StandardOpenOption.CREATE
    )

    ()
  }

  def switchUser(currentUserData: Option[UserData], requestedUser: User): UUID = {
    currentUserData match {
      case None =>
        addUserData(UserData(requestedUser, List.empty))
      case Some(data) =>
        val currentUser = data.user
        val newSwitchable =
          data.switchableUsers.filterNot(_.login == requestedUser.login) :+ currentUser
        val newUserData =
          UserData(requestedUser, newSwitchable)
        addUserData(newUserData)
    }
  }

  def addUserData(userData: UserData): UUID = {
    val uuid = generateUniqueUUID()
    appendSessionsFile(uuid, userData.user, userData.switchableUsers)
    storeUser(userData.user.login)
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

  def getUserData(id: Option[UUID]): Option[UserData] =
    id.flatMap(usersData.get).map { case (user, switchableUsers) =>
      UserData(user, switchableUsers)
    }
}
