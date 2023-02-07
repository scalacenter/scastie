package com.olegych.scastie.storage.mongodb

import org.mongodb.scala._
import play.api.libs.json._

trait GenericMongoContainer {
  val mongoUri: String
  val database: MongoDatabase
  protected val client: MongoClient

  // TODO: Change client logic to use provided codecs
  // MongoDB client provides its own BSON converter, but would require changes in API.
  // Instead we reuse our JSON codecs and create BSON from the generated JSON.
  protected def toBson[T](obj: T)(
    implicit writes: Writes[T]
  ): Document = {
    val json = Json.toJson(obj).toString
    Document.apply(json)
  }

  protected def fromBson[T](obj: Document)(
    implicit reads: Reads[T]
  ): Option[T] = {
    Json.parse(obj.toJson()).asOpt[T]
  }
}
