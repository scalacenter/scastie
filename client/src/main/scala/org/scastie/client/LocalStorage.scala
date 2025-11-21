package org.scastie
package client

import java.util.UUID
import io.circe.syntax._
import io.circe._
import io.circe.parser._
import org.scalajs.dom
import org.scalajs.dom.window.localStorage

object LocalStorage {
  private val stateKey = "state"
  private val clientUuidKey = "clientUuid"

  def save(state: ScastieState): Unit = {
    localStorage.setItem(stateKey, state.asJson.noSpaces)
  }

  def load: Option[ScastieState] = {
    try {
      Option(localStorage.getItem(stateKey))
        .flatMap(raw => decode[ScastieState](raw).toOption)
    } catch {
      case e: Exception =>
        dom.console.log(e.toString)
        None
    }
  }

  def getOrCreateClientUuid(): String = {
    try {
      Option(localStorage.getItem(clientUuidKey)) match {
        case Some(uuid) => uuid
        case None =>
          val newUuid = UUID.randomUUID().toString
          localStorage.setItem(clientUuidKey, newUuid)
          newUuid
      }
    } catch {
      case e: Exception =>
        dom.console.log(s"Failed to get/create client UUID: $e")
        UUID.randomUUID().toString
    }
  }
}
