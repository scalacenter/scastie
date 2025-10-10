package org.scastie.storage.filesystem

import java.nio.file._
import scala.concurrent.Future
import scala.util.Try

import org.scastie.storage.PolicyAcceptance
import org.scastie.storage.UserLogin
import org.scastie.storage.UsersContainer

import io.circe._
import io.circe.parser._
import io.circe.syntax._

trait FilesystemUsersContainer extends UsersContainer with GenericFilesystemContainer {
  val root: Path

  def addNewUser(user: UserLogin): Future[Boolean] = setPrivacyPolicyResponse(user, true)

  def deleteUser(user: UserLogin): Future[Boolean] = Future {
    val userDir = root.resolve(user.login)
    val privacyPolicyFile = userDir.resolve("policy-acceptance.json")

    Try {
      Files.deleteIfExists(privacyPolicyFile)
    }.isSuccess
  }

  def setPrivacyPolicyResponse(user: UserLogin, status: Boolean): Future[Boolean] = Future {
    val userDir = root.resolve(user.login)
    val privacyPolicyFile = userDir.resolve("policy-acceptance.json")

    Try {
      if (!Files.exists(userDir)) Files.createDirectory(userDir)

      Files.write(privacyPolicyFile, PolicyAcceptance(user.login, status).asJson.noSpaces.getBytes())
    }.isSuccess
  }

  def getPrivacyPolicyResponse(user: UserLogin): Future[Boolean] = Future {
    val userDir = root.resolve(user.login)
    val privacyPolicyFile = userDir.resolve("policy-acceptance.json")

    val maybePrivacyPolicy =
      if (Files.exists(privacyPolicyFile)) {
        val response = new String(Files.readAllBytes(privacyPolicyFile))
        decode[PolicyAcceptance](response).toOption
      } else {
        None
      }

    maybePrivacyPolicy.map(_.acceptedPrivacyPolicy).getOrElse(true)
  }

}
