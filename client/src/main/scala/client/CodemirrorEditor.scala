package client

import japgolly.scalajs.react._, vdom.all._

import codemirror.TextAreaEditor

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLTextAreaElement

import scala.scalajs._

object CodeMirrorEditor {
  private val texteareaRef = Ref[HTMLTextAreaElement]("editor-textearea")

  private[CodeMirrorEditor] case class State(
      editor: Option[TextAreaEditor] = None
  )

  case class Handler(
      onChange: String => Callback
  )

  case class Settings(
      value: String,
      theme: String
  )

  private[CodeMirrorEditor] class Backend(
      scope: BackendScope[(Settings, Handler), State]) {
    def start(): Callback = {
      scope.props.flatMap {
        case (settings, handler) =>
          val editor = texteareaRef(scope).map { textArea =>
            val options = js
              .Dictionary[Any](
                "mode"                    -> "text/x-scala",
                "lineNumbers"             -> false,
                "lineWrapping"            -> false,
                "tabSize"                 -> 2,
                "indentWithTabs"          -> false,
                "theme"                   -> s"solarized ${settings.theme}",
                "smartIndent"             -> true,
                "keyMap"                  -> "sublime",
                "scrollPastEnd"           -> true,
                "scrollbarStyle"          -> "simple",
                "autoCloseBrackets"       -> true,
                "matchBrackets"           -> true,
                "showCursorWhenSelecting" -> true,
                "autofocus"               -> true,
                "highlightSelectionMatches" -> js.Dictionary(
                  "showToken" -> js.Dynamic.global.RegExp("\\w")
                ),
                "extraKeys" -> js.Dictionary("Tab" -> "defaultTab")
              )
              .asInstanceOf[codemirror.Options]

            val editor0 = codemirror.CodeMirror.fromTextArea(textArea, options)

            editor0.getDoc.setValue(settings.value)

            dom.console.log(settings.value)

            editor0.onChange((_, _) =>
              handler.onChange(editor0.getDoc().getValue()).runNow)

            editor0
          }.toOption

          scope.modState(_.copy(editor = editor))
      }
    }
    def stop() = {
      scope.modState { s =>
        s.editor.map(_.toTextArea())
        s.copy(editor = None)
      }
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

    Callback(setCode())
  }

  private val component =
    ReactComponentB[(Settings, Handler)]("CodeMirrorEditor")
      .initialState(State())
      .backend(new Backend(_))
      .renderPS {
        case (scope, (props, handler), _) =>
          textarea(
            defaultValue := props.value,
            onChange ==> handler.onChange,
            ref := texteareaRef,
            autoComplete := "off"
          )
      }
      .componentWillReceiveProps { v =>
        val (current, _) = v.currentProps
        val (next, _)    = v.nextProps
        val state        = v.currentState

        state.editor
          .map(editor => runDelta(editor, state, current, next))
          .getOrElse(Callback(()))
      }
      .componentDidMount(_.backend.start())
      .componentWillUnmount(_.backend.stop())
      .build

  def apply(props: Settings, handler: Handler) = component((props, handler))
}
