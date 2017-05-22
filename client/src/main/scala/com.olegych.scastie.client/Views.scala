package com.olegych.scastie
package client

import org.scalajs.dom

sealed trait View
object View {
  case object Editor extends View
  case object BuildSettings extends View
  case object CodeSnippets extends View

  import upickle.default._

  implicit val pkl: ReadWriter[View] =
    macroRW[Editor.type] merge
      macroRW[CodeSnippets.type] merge
      macroRW[BuildSettings.type]

  val isMac = dom.window.navigator.userAgent.contains("Mac")
  val ctrl = if (isMac) "⌘" else "Ctrl"
}
