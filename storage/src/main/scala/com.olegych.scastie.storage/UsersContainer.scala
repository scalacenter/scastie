package com.olegych.scastie.storage

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@deprecated("Scheduled for removal", "2023-04-30")
trait UsersContainer {
  protected implicit val ec: ExecutionContext

  def addNewUser(user: UserLogin): Future[Boolean]
  def deleteUser(user: UserLogin): Future[Boolean]
  def setPrivacyPolicyResponse(user: UserLogin, status: Boolean = true): Future[Boolean]
  def getPrivacyPolicyResponse(user: UserLogin): Future[Boolean]

}
