package org.scastie.client.components.editor

import org.scastie.api
import japgolly.scalajs.react._
import org.scalajs.dom
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._
import typings.codemirrorLint.mod._
import scala.concurrent.duration._
import scala.scalajs.js.timers._
import java.util.concurrent.atomic.AtomicLong

import scalajs.js
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalajs.js.Thenable.Implicits._
import js.JSConverters._

object MetalsDiagnostics {
  private val generation = new AtomicLong(0L)
  def nextGeneration(): Long = generation.incrementAndGet()
  def currentGeneration: Long = generation.get()
}

trait MetalsDiagnostics extends MetalsClient with DebouncingCapabilities {
  private val myGeneration: Long = MetalsDiagnostics.nextGeneration()

  private def debounce(fn: OnChange): OnChange = {
    var timeout: js.UndefOr[js.timers.SetTimeoutHandle] = js.undefined

    (code: String, view: EditorView) => {
      timeout.foreach(clearTimeout)
      timeout = setTimeout(1500.millis) {
        if (myGeneration == MetalsDiagnostics.currentGeneration) {
          fn(code, view)
        }
      }
    }
  }

  def metalsDiags = onChangeCallback(debounce, (code, view) => ifSupported {
    makeRequest(toLSPRequest(code, 0), "diagnostics").map { maybeText =>
      if (myGeneration == MetalsDiagnostics.currentGeneration) {
        parseMetalsResponse[Set[api.Problem]](maybeText).map { problems =>
          val diags = problems.flatMap(CodeEditor.problemToDiagnostics(_, view.state.doc)).toJSArray
          view.update(js.Array(view.state.update(setDiagnostics(view.state, diags))))
        }
      } else None
    }
  })
}
