package com.olegych.scastie.client.components.editor

import com.olegych.scastie.client.components._
import com.olegych.scastie.api

import japgolly.scalajs.react.Reusability

object EditorReusability {
  implicit val reusability: Reusability[Editor] =
    Reusability.derive[Editor]
}

