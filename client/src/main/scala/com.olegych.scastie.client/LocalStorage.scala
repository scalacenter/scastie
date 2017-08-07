package com.olegych.scastie
package client

import play.api.libs.json.Json

import scala.util.Try

import org.scalajs.dom.window.localStorage

object LocalStorage {
  private val stateKey = "state"

  def save(state: ScastieState): Unit =
    localStorage.setItem(stateKey, Json.stringify(Json.toJson(state)))

  def load: Option[ScastieState] =
    Option(localStorage.getItem(stateKey))
      .flatMap(raw => Json.fromJson[ScastieState](Json.parse(raw)).asOpt)
}
