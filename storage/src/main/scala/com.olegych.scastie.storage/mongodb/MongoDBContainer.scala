package com.olegych.scastie.storage.mongodb

import com.typesafe.config.ConfigFactory
import org.mongodb.scala._

import scala.concurrent.ExecutionContext

class MongoDBContainer(defaultConfig: Boolean = false)(
  implicit val ec: ExecutionContext
) extends MongoDBUsersContainer with MongoDBSnippetsContainer {

  val mongoUri = {
    if (defaultConfig) s"mongodb://localhost:27017/scastie"
    else {
      val config       = ConfigFactory.load().getConfig("scastie.mongodb")
      val user         = config.getString("user")
      val password     = config.getString("password")
      val databaseName = config.getString("database")
      val host         = config.getString("host")
      val port         = config.getInt("port")
      s"mongodb://$user:$password@$host:$port/$databaseName"
    }
  }

  protected val client: MongoClient = MongoClient(mongoUri)
  val database: MongoDatabase       = client.getDatabase("snippets")
}
