package com.olegych.scastie
package client

import org.scalajs.dom.window.localStorage

import upickle.default.{write => uwrite, read => uread}

object LocalStorage {
  private val stateKey = "state"
  def save(state: App.State): Unit = 
    localStorage.setItem(stateKey, uwrite(state))

  def load: Option[App.State] = 
    Option(localStorage.getItem(stateKey)).map(raw => uread[App.State](raw))
}
