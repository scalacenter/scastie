package com.olegych.scastie.client.components.editor

import japgolly.scalajs.react._, vdom.all._

import org.scalajs.dom.raw.HTMLTextAreaElement
import org.scalajs.dom.ext.KeyCode

private[editor] class EditorBackend(scope: BackendScope[Editor, EditorState]) {
  private val codemirrorTextarea = Ref[HTMLTextAreaElement]

  def render(props: Editor): VdomElement = {
    div(cls := "editor-wrapper")(
      textarea.withRef(codemirrorTextarea)(
        defaultValue := props.code,
        name := "CodeArea",
        autoComplete := "off"
      )
    )
  }

  def stop(): Callback = {
    scope.modState { s =>
      s.editor.foreach(_.toTextArea())
      s.copy(editor = None)
    }
  }

  def start(): Callback = scope.props.flatMap { props =>
    val editor = codemirror.CodeMirror.fromTextArea(
      codemirrorTextarea.unsafeGet(),
      EditorOptions(props, scope)
    )

    editor.onChanges { (e, _) =>
      props.codeChange(e.getDoc().getValue()).runNow()
    }

    val setEditor = scope.modState(_.copy(editor = Some(editor)))

    val applyDeltas = scope.state.flatMap { state =>
      RunDelta(
        editor = editor,
        currentProps = None,
        nextProps = props,
        state = state,
        modState = f => scope.modState(f)
      )
    }

    val foldCode = CodeFoldingAnnotations(
      editor = editor,
      props = props
    )

    val refresh = Callback(editor.refresh())

    setEditor >>
      applyDeltas >>
      foldCode >>
      refresh
  }
}
