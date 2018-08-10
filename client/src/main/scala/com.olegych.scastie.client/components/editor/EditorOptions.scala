package com.olegych.scastie.client.components.editor

import com.olegych.scastie.client.isMac
import scala.scalajs.js
import org.scalajs.dom

import codemirror.{Editor => CodeMirrorEditor2, CodeMirror => CM}

import japgolly.scalajs.react.BackendScope

private[editor] object EditorOptions {
  def apply(props: Editor,
            scope: BackendScope[Editor, EditorState]): codemirror.Options = {
    val theme =
      if (props.isDarkTheme) "dark"
      else "light"

    val ctrl =
      if (isMac) "Cmd"
      else "Ctrl"

    val highlightSelectionMatches =
      js.Dictionary(
        "showToken" -> js.Dynamic.global.RegExp("\\w")
      )

    def command(f: => Unit): js.Function1[CodeMirrorEditor2, Unit] = {
      ((editor: CodeMirrorEditor2) => f)
    }

    def commandE(
        f: CodeMirrorEditor2 => Unit
    )
      : js.Function1[CodeMirrorEditor2, Unit] = {
      ((editor: CodeMirrorEditor2) => f(editor))
    }

    def autocomplete(editor: CodeMirrorEditor2): Unit = {
      //todo get from inputs
      val hasEnsimeSupport = false
      if (!props.isEmbedded && hasEnsimeSupport) {
        val doc = editor.getDoc()
        val pos = doc.indexFromPos(doc.getCursor())

        props.clearCompletions.runNow()
        props.completeCodeAt(pos).runNow()

        val state = scope.state.runNow()

        if (state.completionState == Idle) {
          state.loadingMessage.show(editor, doc.getCursor())
        }

        scope.modState(_.copy(completionState = Requested)).runNow()
      }
    }

    js.Dictionary[Any](
        "autoRefresh" -> props.isEmbedded,
        "mode" -> "text/x-scala",
        "autofocus" -> !props.isEmbedded,
        "lineNumbers" -> props.showLineNumbers,
        "lineWrapping" -> false,
        "tabSize" -> 2,
        "tabindex" -> 1,
        "indentWithTabs" -> false,
        "theme" -> s"solarized $theme",
        "smartIndent" -> true,
        "keyMap" -> "sublime",
        "scrollPastEnd" -> false,
        "scrollbarStyle" -> "simple",
        "autoCloseBrackets" -> true,
        "matchBrackets" -> true,
        "showCursorWhenSelecting" -> true,
        "highlightSelectionMatches" -> highlightSelectionMatches,
        "extraKeys" -> js.Dictionary(
          "Tab" -> "defaultTab",
          ctrl + "-Enter" -> command(props.run.runNow()),
          ctrl + "-S" -> command(props.saveOrUpdate.runNow()),
          ctrl + "-M" -> command(props.openNewSnippetModal.runNow()),
          ctrl + "-L" -> CM.Pass,
          ctrl + "-E" -> command(props.openEmbeddedModal.runNow()),
          // "Ctrl" + "-Space" -> commandE { editor =>
          //   autocomplete(editor)
          // },
          // "." -> commandE { editor =>
          //   editor.getDoc().replaceSelection(".")
          //   props.codeChange(editor.getDoc().getValue()).runNow()
          //   autocomplete(editor)
          // },
          "Esc" -> command(props.clear.runNow()),
          "F1" -> command(props.toggleHelp.runNow()),
          "F2" -> command(props.toggleTheme.runNow()),
          "F3" -> command(props.toggleConsole.runNow()),
          "F4" -> command(props.toggleWorksheetMode.runNow()),
          "F6" -> command(props.formatCode.runNow()),
          "F7" -> command(props.toggleLineNumbers.runNow()),
          "F8" -> command {
            if (!props.isEmbedded) {
              props.togglePresentationMode.runNow()
              if (!props.isPresentationMode) {
                dom.window
                  .alert("Press F8 again to leave the presentation mode")
              }
            }
          }
        )
      )
      .asInstanceOf[codemirror.Options]
  }
}
