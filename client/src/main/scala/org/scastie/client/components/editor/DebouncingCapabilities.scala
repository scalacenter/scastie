package org.scastie.client.components.editor

import typings.codemirrorState.mod._
import typings.codemirrorView.mod._

import scala.concurrent.duration._
import scala.scalajs.js.timers._

import scalajs.js
import EditorTextOps._


trait DebouncingCapabilities {
  type OnChange = (String, EditorView) => Unit

  private val onChangeFacet: Facet[OnChange, OnChange] = Facet.define[OnChange, OnChange] {
    FacetConfig[OnChange, OnChange]().setCombine(input => over(input.toSeq))
  }

  private def over(functions: Seq[OnChange]): OnChange = {
    (code: String, view: EditorView) => functions.foreach(f => f(code, view))
  }

  protected def onChangeCallback(debounce: OnChange => OnChange, onChange: OnChange): Extension = {
    val debouncedOnChange = debounce(onChange)

    js.Array[Any](
      onChangeFacet.of(debouncedOnChange),
      EditorView.updateListener.of(viewUpdate => {
        if (viewUpdate.docChanged) {
          val content = viewUpdate.state.sliceDoc()
          val onChange = viewUpdate.state.facet[OnChange](onChangeFacet.asInstanceOf[Facet[Any, OnChange]])
          onChange(content, viewUpdate.view)
        }
      })
    )
  }
}
