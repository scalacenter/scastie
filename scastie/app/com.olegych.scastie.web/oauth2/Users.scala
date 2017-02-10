package com.olegych.scastie
package web
package oauth2

import java.nio.file._
import util.Properties

import play.api.Play

import upickle.default.ReadWriter
import java.util.UUID
import scala.collection.parallel.mutable.ParTrieMap

import upickle.default.{Reader, Writer, write => uwrite, read => uread}

object Users {


  private val config = Play.current.configuration
  private val usersFile = Paths.get(config.getString("oauth.users.file").get)
  private val usersSessions = Paths.get(config.getString("oauth.users.sessions").get)

  private lazy val users = {
    val trie = ParTrieMap[UUID, User]()
    readSessions.map{ case (uuid, user) =>
      val pair = uuid -> user
      trie += pair
    }
    trie
  }

  import upickle.Js
  implicit val pkl: ReadWriter[UUID] = 
    ReadWriter[UUID](
      uuid => Js.Str(uuid.toString), 
      { 
        case Js.Str(rawUUID) => UUID.fromString(rawUUID)
      }
    )

  def readSessions(): Array[(UUID, User)] = {
    if(Files.exists(usersSessions)) {
      val content = Files.readAllLines(usersSessions).toArray.mkString(System.lineSeparator)
      uread[Array[(UUID, User)]](content)
    } else Array()
  }

  def appendSession(uuid: UUID, user: User): Unit = {
    val pair = uuid -> user
    users += pair
    val sessions = readSessions()
    val sessions0 = sessions :+ pair

    Files.write(
      usersSessions,
      uwrite(sessions0).getBytes,
      StandardOpenOption.APPEND, StandardOpenOption.CREATE
    )

    ()
  }

  def storeSession(user: User): UUID = {
    val uuid = UUID.randomUUID
    appendSession(uuid, user)
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
