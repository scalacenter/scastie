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
) extends Editor {
  @inline def render: VdomElement = SimpleEditor.hooksComponent(this)
}

object SimpleEditor {

  private def init(props: SimpleEditor, ref: Ref.Simple[Element], editorView: UseStateF[CallbackTo, EditorView]): Callback =
    ref.foreachCB(divRef => {
      val basicExtensions = js.Array[Any](
        typings.codemirror.mod.minimalSetup,
        mod.StreamLanguage.define(typings.codemirrorLegacyModes.modeClikeMod.scala_),
        SyntaxHighlightingTheme.highlightingTheme,
      )
      lazy val readOnlyExtensions = js.Array[Any](
        EditorState.readOnly.of(true),
      )
      lazy val editableExtensions = js.Array[Any](
        lineNumbers(),
        OnChangeHandler(props.onChange),
      )
      val editorStateConfig = EditorStateConfig()
        .setDoc(props.value)
        .setExtensions {
          (if (props.readOnly) readOnlyExtensions else editableExtensions) ++ basicExtensions
        }

      val editor = new EditorView(EditorViewConfig()
        .setState(EditorState.create(editorStateConfig))
        .setParent(divRef)
      )

      editorView.setState(editor)
    })

  private def updateComponent(
      props: SimpleEditor,
      ref: Ref.Simple[Element],
      prevProps: Option[SimpleEditor],
      editorView: UseStateF[CallbackTo, EditorView]
  ): Callback = {
    Editor.updateCode(editorView, props) >>
      Editor.updateTheme(ref, prevProps, props)
  }

  val hooksComponent =
    ScalaFnComponent
      .withHooks[SimpleEditor]
      .useRef(Ref[Element])
      .useState(new EditorView())
      .useRef[Option[SimpleEditor]](None)
      .useLayoutEffectOnMountBy((props, ref, editorView, prevProps) => init(props, ref.value, editorView))
      .useEffectBy((props, ref, editorRef, prevProps) => updateComponent(props, ref.value, prevProps.value, editorRef))
      .useEffectBy((props, _, editorRef, prevProps) => prevProps.set(Some(props)))
      .render((props, ref, _, prevProps) => Editor.render(ref.value))
}
