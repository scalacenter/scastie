package com.olegych.scastie.storage.filesystem

import com.olegych.scastie.storage.PolicyAcceptance
import com.olegych.scastie.storage.UserLogin
import com.olegych.scastie.storage.UsersContainer
import play.api.libs.json.Json

import java.nio.file._
import scala.concurrent.Future
import scala.util.Try

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

      Files.write(privacyPolicyFile, Json.stringify(Json.toJson(PolicyAcceptance(user.login, status))).getBytes())
    }.isSuccess
  }


  def getPrivacyPolicyResponse(user: UserLogin): Future[Boolean] = Future {
    val userDir = root.resolve(user.login)
    val privacyPolicyFile = userDir.resolve("policy-acceptance.json")

    val maybePrivacyPolicy = if (Files.exists(privacyPolicyFile)) {
      val response = new String(Files.readAllBytes(privacyPolicyFile))
      Json.parse(response).asOpt[PolicyAcceptance]
    } else {
      None
    }

    maybePrivacyPolicy.map(_.acceptedPrivacyPolicy).getOrElse(true)
  }
}
