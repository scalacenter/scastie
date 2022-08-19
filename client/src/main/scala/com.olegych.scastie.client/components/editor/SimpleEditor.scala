package com.olegych.scastie.client.components.editor

import com.olegych.scastie.client.components.editor.OnChangeHandler
import japgolly.scalajs.react._
import org.scalajs.dom.Element
import typings.codemirrorLanguage.mod
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._

import scalajs.js
import vdom.all._
import hooks.Hooks.UseStateF

final case class SimpleEditor(
    readOnly: Boolean,
    value: String,
    isDarkTheme: Boolean,
    onChange: String ~=> Callback,
  ) {
    @inline def render: VdomElement = SimpleEditor.hooksComponent(this)
  }

object SimpleEditor {

  private def render(ref: Ref.Simple[Element]): VdomElement =
    div(cls := "editor-wrapper").withRef(ref)

  private def init(props: SimpleEditor, ref: Ref.Simple[Element], editorView: UseStateF[CallbackTo, EditorView]): Callback =

    ref.foreachCB(divRef => {
      lazy val readOnlyExtensions = js.Array[Any](
          typings.codemirror.mod.minimalSetup,
          mod.StreamLanguage.define(typings.codemirrorLegacyModes.clikeMod.scala_),
          SyntaxHighlightingTheme.highlightingTheme,
          EditorState.readOnly.of(true),
        )
      lazy val editableExtensions = js.Array[Any](
          typings.codemirror.mod.basicSetup,
          mod.StreamLanguage.define(typings.codemirrorLegacyModes.clikeMod.scala_),
          SyntaxHighlightingTheme.highlightingTheme,
          OnChangeHandler(props.onChange),
        )
      val editor = new EditorView(new EditorViewConfig {
        state = EditorState.create(new EditorStateConfig {
          doc = props.value
          extensions = if (props.readOnly) readOnlyExtensions else editableExtensions
        })
        parent = divRef
      })

      editorView.setState(editor)
    })

  private def updateCode(editorView: UseStateF[CallbackTo, EditorView], newState: SimpleEditor): Callback = {
    Callback {
      editorView.value.dispatch(TransactionSpec().setChanges(new js.Object {
        var from = 0
        var to = editorView.value.state.doc.length
        var insert = newState.value
      }.asInstanceOf[ChangeSpec]))
    }.when_(editorView.value.state.doc.toString() != newState.value)
  }

  val hooksComponent =
    ScalaFnComponent
      .withHooks[SimpleEditor]
      .useRef(Ref[Element])
      .useState(new EditorView())
      .useLayoutEffectOnMountBy((props, ref, editorView) => init(props, ref.value, editorView))
      .useEffectBy((props, ref, _) => ref.value.foreach( ref => {
        //TODO change logic behind theming
        val theme = if (props.isDarkTheme) "dark" else "light"
        ref.setAttribute("class", s"editor-wrapper cm-s-solarized cm-s-$theme")
      }))
      .useEffectBy((props, _, editorRef) => updateCode(editorRef, props))
      .render((props, ref, _) => render(ref.value))

}
