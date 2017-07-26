package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._, vdom.all._

import codemirror.TextAreaEditor
import org.scalajs.dom.raw.HTMLTextAreaElement

import scala.scalajs._

object CodeMirrorEditor {
  private var textareaRef: HTMLTextAreaElement = _

  private[CodeMirrorEditor] case class State(
      editor: Option[TextAreaEditor] = None
  )

  case class Handler(
      onChange: String => Callback
  )

  case class Settings(
      value: String,
      theme: String,
      readOnly: Boolean
  )

  private[CodeMirrorEditor] class CodeMirrorEditorBackend(
      scope: BackendScope[(Settings, Handler), State]
  ) {
    def start(): Callback = {
      scope.props.flatMap {
        case (settings, handler) =>
          val options = js
            .Dictionary[Any](
              "mode" -> "text/x-scala",
              "readOnly" -> settings.readOnly,
              "lineNumbers" -> false,
              "lineWrapping" -> false,
              "tabSize" -> 2,
              "indentWithTabs" -> false,
              "theme" -> settings.theme,
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
            codemirror.CodeMirror.fromTextArea(textareaRef, options)

          editor.getDoc().setValue(settings.value)

          editor.onChanges(
            (e, _) => handler.onChange(e.getDoc().getValue()).runNow
          )

          scope.modState(_.copy(editor = Some(editor)))
      }
    }
    def stop() = {
      scope.modState { s =>
        s.editor.map(_.toTextArea())
        s.copy(editor = None)
      }
    }

    // via editor.onChanges
    def onChangeF(event: ReactEventFromInput): Callback = {
      Callback(())
    }
  }

  private def runDelta(editor: TextAreaEditor,
                       state: State,
                       current: Settings,
                       next: Settings): Callback = {

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

    Callback(setCode()) >> Callback(setTheme()) >> Callback(editor.refresh())
  }

  private val component =
    ScalaComponent
      .builder[(Settings, Handler)]("CodemirrorEditor")
      .initialState(State())
      .backend(new CodeMirrorEditorBackend(_))
      .renderPS {
        case (scope, (props, handler), _) =>
          textarea.ref(textareaRef = _)(
            value := props.value,
            onChange ==> scope.backend.onChangeF,
            autoComplete := "off"
          )
      }
      .componentWillReceiveProps { scope =>
        val (current, _) = scope.currentProps
        val (next, _) = scope.nextProps
        val state = scope.state

        state.editor
          .map(editor => runDelta(editor, state, current, next))
          .getOrElse(Callback.empty)
      }
      .componentDidMount(_.backend.start())
      .componentWillUnmount(_.backend.stop())
      .build

  def apply(props: Settings, handler: Handler) = component((props, handler))
}
