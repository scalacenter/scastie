package com.olegych.scastie.client.components.editor

import japgolly.scalajs.react._, vdom.all._, extra._

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

      editor.onFocus(_.refresh())

      editor.onChanges(
        (e, _) => props.codeChange(e.getDoc().getValue()).runNow()
      )

      // don't show completions if cursor moves to some other place
      editor.onMouseDown(
        (_, _) => {
          scope
            .modState { state =>
              state.loadingMessage.hide()

              state.copy(completionState = Idle)
            }
            .runNow()
        }
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

      val setEditor =
        scope.modState(_.copy(editor = Some(editor)))

      val applyDeltas =
        scope.state.flatMap(
          state => RunDelta(editor, f => scope.modState(f), state, None, props)
        )

      val delayedRefresh =
        Callback(
          scalajs.js.timers.setTimeout(0)(
            editor.refresh()
          )
        )

      setEditor >> applyDeltas >> delayedRefresh
    }
  }
}
