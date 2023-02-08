package com.olegych.scastie.client.components.editor

import japgolly.scalajs.react._
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._

import scalajs.js

class OnChangeHandler(onChange: String ~=> Callback) extends js.Object {

  private def scalaUpdate: js.Function1[ViewUpdate, Unit] = viewUpdate => {
    if (viewUpdate.docChanged && viewUpdate.startState.doc != viewUpdate.state.doc) {
      onChange(viewUpdate.state.doc.toString).runNow()
    }
  }

  var update = scalaUpdate

}

object OnChangeHandler {
  def apply(onChange: String ~=> Callback): Extension =
    ViewPlugin.define(_ => new OnChangeHandler(onChange)).extension
}
