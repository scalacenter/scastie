package org.scastie.client.components.editor

import org.scastie.api
import japgolly.scalajs.react._
import org.scalajs.dom
import org.scalajs.dom.html
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._
import typings.codemirrorView.mod.{EditorView => CodemirrorEditorView}
import scalajs.js
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalajs.js.Thenable.Implicits._
import js.JSConverters._
import org.scastie.api._

trait MetalsSignatureHelp extends MetalsClient {

  private var currentSignature: Option[SignatureHelpDTO] = None

  private def isCursorInParens(doc: String, cursorPos: Int): Boolean = {
    val openParenPos = doc.lastIndexOf('(', cursorPos - 1)
    val closeParenPos = doc.indexOf(')', cursorPos)
    openParenPos != -1 &&
    closeParenPos != -1 &&
    cursorPos > openParenPos &&
    cursorPos <= closeParenPos
  }

  private def getSignatureTooltips(state: EditorState): js.Array[Tooltip] = {
    val cursorPos = state.selection.main.head.toInt
    val doc = state.doc.toString

    if (isCursorInParens(doc, cursorPos) && currentSignature.isDefined) {
      val sigHelp = currentSignature.get
      val node = getSignatureHelpNode(sigHelp)

      val line = state.doc.lineAt(cursorPos).number
      val showAbove = line > 2

      js.Array(
        js.Dynamic.literal(
          pos = cursorPos.toDouble,
          above = showAbove,
          strictSide = true,
          create = (_: EditorView) => TooltipView(node.domToHtml.get)
        ).asInstanceOf[Tooltip]
      )
    } else {
      js.Array[Tooltip]()
    }
  }

  private val signatureHelpField = {
    val cmState = typings.codemirrorState.mod.^.asInstanceOf[js.Dynamic]
    val cmView = typings.codemirrorView.mod.^.asInstanceOf[js.Dynamic]

    cmState.StateField.define(js.Dynamic.literal(
      create = (state: EditorState) => getSignatureTooltips(state),

      update = (tooltips: js.Array[Tooltip], tr: Transaction) => {
        if (!tr.docChanged && tr.selection == null) {
          tooltips
        } else {
          getSignatureTooltips(tr.state)
        }
      },

      provide = (field: StateField[js.Array[Tooltip]]) => {
        cmView.showTooltip.computeN(
          js.Array(field),
          (state: EditorState) => state.field(field)
        )
      }
    )).asInstanceOf[StateField[js.Array[Tooltip]]]
  }

  private val signatureHelpTrigger = CodemirrorEditorView.updateListener.of(update => {
    if (update.docChanged || update.selectionSet) {
      val cursorPos = update.state.selection.main.head.toInt
      val doc = update.state.doc.toString

      if (isCursorInParens(doc, cursorPos)) {
        requestAndUpdateSignatureHelp(update.view, cursorPos)
      } else {
        currentSignature = None
      }
    }
  })


  private def requestSignatureHelp(code: String, pos: Int): js.Promise[Option[SignatureHelpDTO]] = {
    /* FIXME: For overloaded methods like:
     *   def add(x: Int, y: Int): Int = x + y
     *   def add(x: Int, y: Int)(z: Int): Int = x + y + z
     * always the second signature is selected, even though it's supposed to be the first one.
     */
    ifSupported {
      val request = toLSPRequest(code, pos)
      makeRequest(request, "signatureHelp").map { maybeText =>
        parseMetalsResponse[SignatureHelpDTO](maybeText).filter(_.signatures.nonEmpty)
      }.toJSPromise
    }
  }

  private def requestAndUpdateSignatureHelp(view: EditorView, pos: Int): Unit = {
    requestSignatureHelp(view.state.doc.toString(), pos).foreach {
      case Some(sigHelp) =>
        currentSignature = Some(sigHelp)
        view.dispatch(TransactionSpec())

      case None =>
        currentSignature = None
        view.dispatch(TransactionSpec())
    }
  }

  private def getSignatureHelpNode(sigHelp: SignatureHelpDTO): dom.Node = {
    val sigIdx = sigHelp.activeSignature
    val paramIdx = sigHelp.activeParameter
    val sig = sigHelp.signatures(sigIdx)
    val doc = sig.documentation
    val label = sig.label

    val highlighted = InteractiveProvider.highlightSignatureHelp(label, Some(paramIdx))

    val node = dom.document.createElement("div")
    node.setAttribute("class", "cm-tooltip-signature-help")
    node.innerHTML = s"<pre>$highlighted</pre>"
    node
  }

  def metalsSignatureHelp: js.Array[Any] = js.Array[Any](
    signatureHelpField,
    signatureHelpTrigger
  )
}