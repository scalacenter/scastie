package client

import org.scalajs.dom.window.localStorage

// TODO save other inputs
object LocalStorage {
  private val codeKey = "code"
  def saveCode(code: String): Unit = localStorage.setItem(codeKey, code)
  def loadCode: Option[String] = Option(localStorage.getItem(codeKey))
}
