package com.olegych.scastie.client.components.editor

import japgolly.scalajs.react._, vdom.all._

import org.scalajs.dom.raw.HTMLTextAreaElement
import org.scalajs.dom.ext.KeyCode

private[editor] class EditorBackend(scope: BackendScope[Editor, EditorState]) {
  private var codemirrorTextarea: HTMLTextAreaElement = _

  def render(props: Editor): VdomElement = {
    div(cls := "editor-wrapper")(
      textarea.ref(codemirrorTextarea = _)(
        defaultValue := props.code,
        name := "CodeArea",
        autoComplete := "off"
      )
    )
  }

  def stop(): Callback = {
    scope.modState { s =>
      s.editor.map(_.toTextArea())
      s.copy(editor = None)
    }
  }

  def start(): Callback = {
    scope.props.flatMap { props =>
      val editor =
        codemirror.CodeMirror.fromTextArea(
          codemirrorTextarea,
          EditorOptions(props, scope)
        )

      editor.onChanges(
        (e, _) => props.codeChange(e.getDoc().getValue()).runNow()
      )

      editor.onKeyUp((_, e) => {
        scope
          .modState(
            s =>
              s.copy(
                showTypeButtonPressed = s.showTypeButtonPressed && e.keyCode != KeyCode.Ctrl
            )
          )
          .runNow()
      })

      val setEnsimeHandler =
        EnsimeHandler.setup(editor, scope)

      val setEditor =
        scope.modState(_.copy(editor = Some(editor)))

      val applyDeltas =
        scope.state.flatMap(
          state =>
            RunDelta(
              editor = editor,
              currentProps = None,
              nextProps = props,
              state = state,
              modState = f => scope.modState(f)
          )
        )

      val foldCode =
        CodeFoldingAnnotations(
          editor = editor,
          props = props
        )

      val delayedRefresh =
        Callback(
          scalajs.js.timers.setTimeout(0)(
            editor.refresh()
          )
        )

      setEditor >>
        setEnsimeHandler >>
        applyDeltas >>
        foldCode >>
        delayedRefresh
    }
  }
}
