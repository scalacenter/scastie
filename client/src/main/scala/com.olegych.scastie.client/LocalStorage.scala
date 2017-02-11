package com.olegych.scastie
package client

import scala.util.Try

import org.scalajs.dom.window.localStorage

import upickle.default.{write => uwrite, read => uread}

object LocalStorage {
  private val stateKey = "state"
  def save(state: App.State): Unit =
    localStorage.setItem(stateKey, uwrite(state))

  def load: Option[App.State] =
    Option(localStorage.getItem(stateKey)).flatMap(raw => 
      Try(uread[App.State](raw)).toOption
    )
}
