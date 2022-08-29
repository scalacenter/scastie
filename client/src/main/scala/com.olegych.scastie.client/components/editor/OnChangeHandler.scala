package com.olegych.scastie.client.components.editor

import com.olegych.scastie.api
import japgolly.scalajs.react._
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._
import typings.std.PropertyKey

import scalajs.js

class OnChangeHandler(
  onChange: String ~=> Callback,
) extends PluginValue {

  override var constructor: js.Function = null
  override def hasOwnProperty(v: PropertyKey): Boolean = false
  override def propertyIsEnumerable(v: PropertyKey): Boolean = false

  def scalaUpdate: js.Function1[ViewUpdate, Unit] = viewUpdate => {

    if (viewUpdate.docChanged && viewUpdate.startState.doc != viewUpdate.state.doc) {
      onChange(viewUpdate.state.doc.toString).runNow()
    }
  }

  update = scalaUpdate

}

object OnChangeHandler{
  def apply(
    onChange: String ~=> Callback,
  ): Extension = ViewPlugin.define(_ => new OnChangeHandler(onChange)).extension
}
