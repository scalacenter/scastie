package com.olegych.scastie.client.components.editor

import japgolly.scalajs.react.{BackendScope, Callback}

import codemirror.{TextAreaEditor, CodeMirror}

import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js

private[editor] object EnsimeHandler {
  private def keyHandler(scope: BackendScope[Editor, EditorState],
                         event: dom.KeyboardEvent): Unit = {
    val terminateKeys = Set(
      KeyCode.Space,
      KeyCode.Escape,
      KeyCode.Enter,
      KeyCode.Up,
      KeyCode.Down
    )

    val dot = 46

    scope
      .modState { state =>
        var resultState = state

        resultState = resultState
          .copy(showTypeButtonPressed = event.keyCode == KeyCode.Ctrl)

        // if any of these keys are pressed
        // then user doesn't need completions anymore
        if (terminateKeys.contains(event.keyCode) && !event.ctrlKey) {
          state.loadingMessage.hide()
          resultState = resultState.copy(completionState = Idle)
        }

        // we still show completions but user pressed a key,
        // => render completions again (filters may apply there)
        if (resultState.completionState == Active) {
          // we might need to fetch new completions
          // when user goes backwards
          if (event.keyCode == KeyCode.Backspace) {
            state.editor.foreach { editor =>
              val doc = editor.getDoc()
              val pos = doc.indexFromPos(doc.getCursor()) - 1
              scope.props.runNow().completeCodeAt(pos).runNow()
            }
          }

          resultState = resultState.copy(completionState = NeedRender)
        }

        resultState
      }
      .runNow()
  }

  private def setFindTypeTooltips(
      scope: BackendScope[Editor, EditorState]
  )(event: dom.MouseEvent): Unit = {
    val node = event.target

    if (node != null && node.isInstanceOf[HTMLElement]) {
      val text = node.asInstanceOf[HTMLElement].textContent
      if (text != null) {

        val state = scope.state.runNow()
        val editor = state.editor.get

        // request token under the cursor
        val pos = editor.coordsChar(
          js.Dictionary[Any](
            "left" -> event.clientX,
            "top" -> event.clientY
          ),
          mode = null
        )
        val currToken = editor.getTokenAt(pos, precise = null).string

        // Request type info only if Ctrl is pressed
        if (currToken == text) {
          val s = scope.state.runNow()
          if (s.showTypeButtonPressed) {
            val lastTypeInfo = s.typeAt
            val message =
              if (lastTypeInfo.isEmpty || lastTypeInfo.get.token != currToken) {
                // if it's the first typeAt request
                // OR if user's moved on to a new token
                // then we request new type information with curr token and show "..."
                scope.props
                  .runNow()
                  .requestTypeAt((currToken, editor.getDoc().indexFromPos(pos)))
                  .runNow()
                "..."
              } else {
                s.typeAt.get.typeInfo
              }
            state.hoverMessage.show(node.asInstanceOf[HTMLElement], message)
          }
        }
      }
    }
  }

  def setup(editor: TextAreaEditor,
            scope: BackendScope[Editor, EditorState]): Callback = {

    scope.state.map { state =>
      CodeMirror.on(editor.getWrapperElement(),
                    "mousemove",
                    setFindTypeTooltips(scope))

      editor.onKeyDown((_, event) => keyHandler(scope, event))
    }
  }
}
