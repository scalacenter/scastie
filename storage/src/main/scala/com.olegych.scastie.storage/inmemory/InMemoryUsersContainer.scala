package com.olegych.scastie.storage.inmemory

import com.olegych.scastie.storage.PolicyAcceptance
import com.olegych.scastie.storage.UserLogin
import com.olegych.scastie.storage.UsersContainer

import scala.collection.mutable
import scala.concurrent.Future

trait InMemoryUsersContainer extends UsersContainer {
  val users: mutable.Set[PolicyAcceptance] = mutable.Set[PolicyAcceptance]()

  def addNewUser(user: UserLogin): Future[Boolean] = Future {
    users.add(PolicyAcceptance(user.login))
  }

  def deleteUser(user: UserLogin): Future[Boolean] = Future {
    users.find(_.user == user.login).map(users.remove).isDefined
  }

  def setPrivacyPolicyResponse(user: UserLogin, status: Boolean): Future[Boolean] = Future {
    users.update(users.find(_.user == user.login).getOrElse(PolicyAcceptance(user.login, false)), status)
    true
  }

  def getPrivacyPolicyResponse(user: UserLogin): Future[Boolean] = Future {
    val maybeUser = users.find(_.user == user.login)
    // If there is no user in database we set it to true, we don't need to hold new users as by default they accept the policy
    // All user containers will be removed after said period of time
    maybeUser.map(_.acceptedPrivacyPolicy).getOrElse(true)
  }
}
