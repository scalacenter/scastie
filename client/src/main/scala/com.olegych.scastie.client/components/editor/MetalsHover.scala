package com.olegych.scastie.client.components.editor

import com.olegych.scastie.api
import japgolly.scalajs.react._
import org.scalajs.dom
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._

import scalajs.js
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalajs.js.Thenable.Implicits._
import js.JSConverters._

trait MetalsHover extends MetalsClient {
  private val hovers = hoverTooltip((view, pos, _) => ifSupported {
    val request = toLSPRequest(view.state.doc.toString(), pos.toInt)

    makeRequest(request, "hover").map(maybeText =>
      parseMetalsResponse[api.HoverDTO](maybeText).map { hover =>
        val hoverF: js.Function1[EditorView, TooltipView] = _ => {
          val node = dom.document.createElement("div")
          node.innerHTML = InteractiveProvider.marked(hover.content)
          TooltipView(node.domToHtml.get)
        }

        view.state.wordAt(pos) match {
          case range: SelectionRange => Tooltip(hoverF, range.from)
            .setEnd(range.to)
          case _ => Tooltip(hoverF, pos)
        }
      }
    )
  }.map(_.getOrElse(null)).toJSPromise)

  def metalsHover = hovers
}
