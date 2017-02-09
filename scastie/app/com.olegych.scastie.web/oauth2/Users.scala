package com.olegych.scastie
package web
package oauth2

import java.nio.file._
import util.Properties

import play.api.Play

import java.util.UUID
import scala.collection.parallel.mutable.ParTrieMap

object Users {

  private val config = Play.current.configuration
  private val usersFile = Paths.get(config.getString("oauth.users.file").get)

  private val users = ParTrieMap[UUID, User]()

  def storeSession(user: User): UUID = {
    val uuid = UUID.randomUUID
    users += uuid -> user
    uuid
  }

  def get(uuid: UUID): Option[User] = {
    users.get(uuid)
  }

  def add(login: String): Unit = {
    if(!exists(login)) {
      Files.write(usersFile, login.getBytes, StandardOpenOption.APPEND, StandardOpenOption.CREATE)
      ()
    }
  }

  def exists(login: String): Boolean = {
    if (Files.exists(usersFile)) {
      Files.readAllLines(usersFile).toArray.contains(login)
    }
    else false
  }
}
