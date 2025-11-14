package org.scastie.storage.mongodb

import com.typesafe.config.ConfigFactory
import org.mongodb.scala._

import scala.concurrent.ExecutionContext

class MongoDBContainer(defaultConfig: Boolean = false)(
  implicit val ec: ExecutionContext
) extends MongoDBUsersContainer with MongoDBSnippetsContainer {

  val (client0, database0) = {
    if (defaultConfig) {
      val mongoUri = s"mongodb://localhost:27017/scastie"
      val client: MongoClient = MongoClient(mongoUri)
      (client, client.getDatabase("snippets"))
    } else {
      val config       = ConfigFactory.load().getConfig("scastie.mongodb")
      val user         = config.getString("user")
      val password     = config.getString("password")
      val databaseName = config.getString("database")
      val host         = config.getString("host")
      val port         = config.getInt("port")

      val mongoUri = s"mongodb://$user:$password@$host:$port/$databaseName"
      val client: MongoClient = MongoClient(mongoUri)
      (client, client.getDatabase(databaseName))
    }
  }

  protected val client: MongoClient = client0
  protected val database: MongoDatabase = database0

}
