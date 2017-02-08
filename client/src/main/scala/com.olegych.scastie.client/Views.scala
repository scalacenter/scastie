package com.olegych.scastie
package client

sealed trait View
object View {
  case object Editor extends View
  case object Settings extends View

  import upickle.default._

  implicit val pkl: ReadWriter[View] =
    macroRW[Editor.type] merge macroRW[Settings.type]
}
