package com.olegych.scastie.client.components.editor

import com.olegych.scastie.api
import com.olegych.scastie.api.AttachedDom
import com.olegych.scastie.api.Html
import com.olegych.scastie.api.Value
import japgolly.scalajs.react._
import org.scalajs.dom
import org.scalajs.dom.HTMLElement
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._

import scalajs.js
import hooks.Hooks.UseStateF

object TypeDecorationProvider {

  class TypeDecoration(instrumentation: api.Instrumentation) extends WidgetType {
    override def toDOM(view: EditorView): HTMLElement = {
      val (valueString, typeString) = instrumentation.render match {
        case AttachedDom(uuid, folded) => ???
        case Html(a, folded) => ???
        case Value(v, className) => v -> className
      }

      val wrap = dom.document.createElement("span")
      wrap.setAttribute("aria-hidden", "true")
      wrap.setAttribute("class", "cm-linewidget")
      val textBody = dom.document.createElement("pre")
      textBody.setAttribute("class", "inline")
      val value = dom.document.createElement("span")
      value.setAttribute("class", "cm-variable")
      value.innerText = s"$valueString: "
      val typ = dom.document.createElement("span")
      typ.setAttribute("class", "cm-type")
      typ.innerText = typeString
      textBody.append(value, typ)
      wrap.append(textBody)
      wrap.domAsHtml
    }
  }


  def createDecorations(instrumentations: Set[api.Instrumentation]): DecorationSet = {
    val deco = instrumentations.map { instrumentation =>
      {
        Decoration
          .widget(WidgetDecorationSpec(new TypeDecoration(instrumentation)))
          .range(instrumentation.position.end)
          .asInstanceOf[Range[Decoration]]
      }
    }.toSeq
    val x: js.Array[Range[Decoration]] = js.Array(deco: _*)
    Decoration.set(x, true)
  }

  val addTypeDecorations = StateEffect.define[DecorationSet]()
  val filterTypeDecorations = StateEffect.define[DecorationSet]()

  def updateState(previousValue: DecorationSet, transaction: Transaction): DecorationSet = {
    val (addEffects, filterEffects) = transaction.effects
      .filter(effect => { effect.is(addTypeDecorations) | effect.is(filterTypeDecorations) })
      .partition(_.is(addTypeDecorations))

    addEffects.headOption match {
      case Some(stateEffect) => {
        val decorationSet = stateEffect.value.asInstanceOf[DecorationSet]
        if (decorationSet.size > 0) decorationSet else Decoration.none
      }
      case _ =>
        previousValue
    }
  }

  def updateTypeDecorations(
      editorView: UseStateF[CallbackTo, EditorView],
      prevProps: Option[Editor],
      props: Editor
  ): Callback =
    Callback {
      println("i'm in")
      val decorations = createDecorations(props.instrumentations)
      val addTypesEffect = addTypeDecorations.of(decorations)
      editorView.value.dispatch(TransactionSpec().setEffects(addTypesEffect.asInstanceOf[StateEffect[Any]]))
  }.when_(prevProps.isDefined &&
    (editorView.value.state.doc.toString() == props.code && props.instrumentations != prevProps.get.instrumentations)
  )

  def stateFieldSpec(props: Editor) =
    StateFieldSpec[DecorationSet](
      create = _ => createDecorations(props.instrumentations),
      update = updateState,
    ).setProvide(v => EditorView.decorations.from(v))

  def apply(props: Editor): Extension = StateField.define(stateFieldSpec(props)).extension
}
