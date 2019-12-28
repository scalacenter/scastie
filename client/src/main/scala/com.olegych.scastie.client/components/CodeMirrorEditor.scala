package com.olegych.scastie
package client
package components

import codemirror.TextAreaEditor
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._
import org.scalajs.dom.raw.HTMLTextAreaElement

import scala.scalajs._

final case class CodeMirrorEditor(
    onChange: String ~=> Callback,
    value: String,
    theme: String,
    readOnly: Boolean
) {
  @inline def render: VdomElement = CodeMirrorEditor.component(this)
}

object CodeMirrorEditor {
  implicit val reusability: Reusability[CodeMirrorEditor] =
    Reusability.derive[CodeMirrorEditor]

  private[CodeMirrorEditor] case class State(
      editor: Option[TextAreaEditor] = None
  )

  private[CodeMirrorEditor] class CodeMirrorEditorBackend(scope: BackendScope[CodeMirrorEditor, State]) {
    private val textareaRef = Ref[HTMLTextAreaElement]

    def render(props: CodeMirrorEditor): VdomElement = {
      textarea.withRef(textareaRef)(
        if (props.readOnly) value := props.value else defaultValue := props.value,
        readOnly := props.readOnly,
        autoComplete := "off"
      )
    }

    def start(): Callback = {
      scope.props.flatMap { props =>
        val options = js
          .Dictionary[Any](
            "mode" -> "text/x-scala",
            "readOnly" -> props.readOnly,
            "lineNumbers" -> false,
            "lineWrapping" -> false,
            "tabSize" -> 2,
            "indentWithTabs" -> false,
            "theme" -> props.theme,
            "smartIndent" -> true,
            "keyMap" -> "sublime",
            "scrollPastEnd" -> false,
            "scrollbarStyle" -> "simple",
            "autoCloseBrackets" -> true,
            "matchBrackets" -> true,
            "showCursorWhenSelecting" -> true,
            "autofocus" -> false,
            "highlightSelectionMatches" -> js.Dictionary(
              "showToken" -> js.Dynamic.global.RegExp("\\w")
            ),
            "extraKeys" -> js.Dictionary("Tab" -> "defaultTab")
          )
          .asInstanceOf[codemirror.Options]

        val editor =
          codemirror.CodeMirror.fromTextArea(textareaRef.unsafeGet(), options)
        editor.getDoc().setValue(props.value)
        editor.onChanges(
          (e, _) => props.onChange(e.getDoc().getValue()).runNow
        )
        scope.modState(_.copy(editor = Some(editor)))
      }
    }
    def stop(): CallbackTo[Unit] = {
      scope.modState { s =>
        s.editor.foreach(_.toTextArea())
        s.copy(editor = None)
      }
    }
  }

  private def runDelta(editor: TextAreaEditor, state: State, current: CodeMirrorEditor, next: CodeMirrorEditor): Callback = {

    def setCode(): Unit = {
      if (current.value != next.value) {
        val doc = editor.getDoc()
        if (doc.getValue() != next.value) {
          val cursor = doc.getCursor()
          doc.setValue(next.value)
          doc.setCursor(cursor)
        }
      }
    }

    def setTheme(): Unit = {
      if (current.theme != next.theme) {
        editor.setOption("theme", next.theme)
      }
    }

    Callback(setCode()) >>
      Callback(setTheme()) >>
      Callback(editor.refresh())
  }

  private val component =
    ScalaComponent
      .builder[CodeMirrorEditor]("CodeMirrorEditor")
      .initialState(State())
      .renderBackend[CodeMirrorEditorBackend]
      .componentWillReceiveProps { scope =>
        val current = scope.currentProps
        val next = scope.nextProps
        val state = scope.state

        state.editor
          .map(editor => runDelta(editor, state, current, next))
          .getOrElse(Callback.empty)
      }
      .componentDidMount(_.backend.start())
      .componentDidUpdate { u =>
        Callback.traverseOption(u.currentState.editor) { e =>
          Callback(
            if (u.currentProps.readOnly) e.refresh()
            else e.focus()
          )
        }
      }
      .componentWillUnmount(_.backend.stop())
      .build
}
