package com.olegych.scastie.storage.mongodb

import com.olegych.scastie.storage._
import org.mongodb.scala._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model._

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

trait MongoDBUsersContainer extends UsersContainer with GenericMongoContainer {
  lazy val users = {
    val db = database.getCollection[Document]("users")
    Await.result(db.createIndex(Indexes.ascending("user"), IndexOptions().unique(true)).head(), Duration.Inf)
    db
  }

  def deleteUser(user: UserLogin): Future[Boolean] =
    users.deleteOne(Document("user" -> user.login)).map(_.wasAcknowledged).head()

  def addNewUser(user: UserLogin): Future[Boolean] =
    users.insertOne(toBson(PolicyAcceptance(user.login))).map(_.wasAcknowledged).head()

  def setPrivacyPolicyResponse(user: UserLogin, status: Boolean = true): Future[Boolean] =
    users.updateOne(Document("user" -> user.login), set("acceptedPrivacyPolicy", status)).map(_.wasAcknowledged).head()

  def getPrivacyPolicyResponse(user: UserLogin): Future[Boolean] = users
    .find(Document("user" -> user.login))
    .first()
    .headOption()
    .map(_.flatMap(fromBson[PolicyAcceptance]).map(_.acceptedPrivacyPolicy).getOrElse(true))

}
