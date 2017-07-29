package com.olegych.scastie.client

import org.scalajs.dom

sealed trait View
object View {
  case object Editor extends View
  case object BuildSettings extends View
  case object CodeSnippets extends View
  case object Status extends View

  val isMac = dom.window.navigator.userAgent.contains("Mac")
  val ctrl = if (isMac) "⌘" else "Ctrl"
}
