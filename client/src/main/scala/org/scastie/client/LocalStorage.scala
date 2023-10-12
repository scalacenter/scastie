package org.scastie
package client


import io.circe.syntax._
import io.circe._
import io.circe.parser._
import org.scalajs.dom
import org.scalajs.dom.window.localStorage

object LocalStorage {
  private val stateKey = "state"

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
}
