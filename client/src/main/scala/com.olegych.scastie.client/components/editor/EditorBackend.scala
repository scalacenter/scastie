package com.olegych.scastie.client.components.editor

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.builder.Lifecycle
import japgolly.scalajs.react.vdom.all._
import org.scalajs.dom.raw.HTMLTextAreaElement

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

    val refresh = Callback(editor.refresh())

    setEditor >>
      applyDeltas >>
      refresh
  }

  def update(scope: Lifecycle.ComponentWillReceiveProps[Editor, EditorState, EditorBackend]): Callback = {
    scope.state.editor
      .map { editor =>
        RunDelta(
          editor = editor,
          currentProps = Some(scope.currentProps),
          nextProps = scope.nextProps,
          state = scope.state,
          modState = f => scope.modState(f)
        ) >> CodeFolds.fold(
          editor = editor,
          props = scope.nextProps,
          modState = f => scope.modState(f)
        ) >> CodeReadOnly.markReadOnly(
          editor = editor,
          props = scope.nextProps,
          modState = f => scope.modState(f)
        )
      }
      .getOrElse(Callback.empty)
  }
}
