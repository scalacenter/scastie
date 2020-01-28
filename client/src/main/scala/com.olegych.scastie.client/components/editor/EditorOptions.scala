package com.olegych.scastie.client.components.editor

import com.olegych.scastie.client.isMac
import com.olegych.scastie.client.isMobile
import scala.scalajs.js
import org.scalajs.dom

import codemirror.{Editor => CodeMirrorEditor2, CodeMirror => CM}

import japgolly.scalajs.react.BackendScope

object EditorOptions {
  object Keys {
    val ctrl = if (isMac) "Cmd" else "Ctrl"

    val saveOrUpdate = ctrl + "-Enter"
    val openNew = ctrl + "-M"
    val clear = "Esc"
    val help = "F1"
    val console = "F3"
    val format = "F6"
    val lineNumbers = "F7"
    val presentation = "F8"
  }

  def apply(props: Editor, scope: BackendScope[Editor, EditorState]): codemirror.Options = {
    val theme =
      if (props.isDarkTheme) "dark"
      else "light"

    val highlightSelectionMatches =
      if (isMobile) false
      else
        js.Dictionary(
          //messes up input on mobile browsers
          "showToken" -> js.Dynamic.global.RegExp("\\w")
        )

    def command(f: => Unit): js.Function1[CodeMirrorEditor2, Unit] = { ((editor: CodeMirrorEditor2) => f)
    }

    def commandE(
        f: CodeMirrorEditor2 => Unit
    ): js.Function1[CodeMirrorEditor2, Unit] = { ((editor: CodeMirrorEditor2) => f(editor))
    }

    import Keys._
    val indentWithTabs = false
    js.Dictionary[Any](
        "autoRefresh" -> props.isEmbedded,
        "mode" -> "text/x-scala",
        "autofocus" -> !props.isEmbedded,
        "lineNumbers" -> props.showLineNumbers,
        "lineWrapping" -> false,
        "tabSize" -> 2,
        "tabindex" -> 1,
        "indentWithTabs" -> indentWithTabs,
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
          "Tab" -> commandE { e =>
            if (e.somethingSelected()) e.indentSelection("add")
            else e.execCommand(if (indentWithTabs) "insertTab" else "insertSoftTab")
          },
          saveOrUpdate -> command(props.saveOrUpdate.runNow()),
          ctrl + "-S" -> command(props.saveOrUpdate.runNow()),
          openNew -> command(props.openNewSnippetModal.runNow()),
          ctrl + "-L" -> CM.Pass,
          clear -> command(props.clear.runNow()),
          help -> command(props.toggleHelp.runNow()),
          console -> command(props.toggleConsole.runNow()),
          format -> command(props.formatCode.runNow()),
          lineNumbers -> command(props.toggleLineNumbers.runNow()),
          presentation -> command {
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
