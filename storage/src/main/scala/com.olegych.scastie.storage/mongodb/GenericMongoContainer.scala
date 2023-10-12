package com.olegych.scastie.storage.mongodb

import org.mongodb.scala._
import io.circe._
import io.circe.parser._
import io.circe.syntax._

trait GenericMongoContainer {
  val mongoUri: String
  val database: MongoDatabase
  protected val client: MongoClient

  // TODO: Change client logic to use provided codecs
  // MongoDB client provides its own BSON converter, but would require changes in API.
  // Instead we reuse our JSON codecs and create BSON from the generated JSON.
  protected def toBson[T](obj: T)(
    implicit writes: Encoder[T]
  ): Document = {
    val json = obj.asJson.noSpaces
    Document.apply(json)
  }

  protected def fromBson[T](obj: Document)(
    implicit reads: Decoder[T]
  ): Option[T] = {
    decode[T](obj.toJson()).toOption
  }
}
