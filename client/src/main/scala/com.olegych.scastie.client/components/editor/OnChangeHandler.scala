package com.olegych.scastie.client.components.editor

import com.olegych.scastie.api
import japgolly.scalajs.react._
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._
import typings.std.PropertyKey

import scalajs.js

class OnChangeHandler(
  onChange: String ~=> Callback,
  invalidateDecorations: api.Position ~=> Callback = Reusable.always(_ => Callback.empty)
) extends PluginValue {

  override var constructor: js.Function = null
  override def hasOwnProperty(v: PropertyKey): Boolean = false
  override def propertyIsEnumerable(v: PropertyKey): Boolean = false

  def scalaUpdate: js.Function1[ViewUpdate, Unit] = viewUpdate => {
    // def rangeChangeHandler: js.Function4[Double, Double, Double, Double, Unit] = (fromA, toA, fromB, toB) => {
    //   val startLine = viewUpdate.startState.doc.lineAt(fromA)
    //   val endLine = viewUpdate.startState.doc.lineAt(toA)
    //   invalidateDecorations(api.Position(startLine.from.toInt, endLine.from.toInt)).runNow()
    // }

    if (viewUpdate.docChanged && viewUpdate.startState.doc != viewUpdate.state.doc) {
      // viewUpdate.changes.iterChangedRanges(rangeChangeHandler)
      onChange(viewUpdate.state.doc.toString).runNow()
    }
  }


  update = scalaUpdate

}

object OnChangeHandler {
  def apply(
    onChange: String ~=> Callback,
    invalidateDecorations: api.Position ~=> Callback = Reusable.always(_ => Callback.empty)
  ): Extension = ViewPlugin.define(_ => new OnChangeHandler(onChange, invalidateDecorations)).extension
}
