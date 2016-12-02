package client

import japgolly.scalajs.react._, vdom.all._

import codemirror.TextAreaEditor

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

  private[CodeMirrorEditor] class Backend(scope: BackendScope[(Settings, Handler), State]) {
    def start(): Callback = {
      scope.props.flatMap{ case (settings, handler) =>
        val editor = texteareaRef(scope).map{textArea =>
          val options = js.Dictionary[Any](
            "mode"                      -> "text/x-scala",
            "lineNumbers"               -> false,
            "lineWrapping"              -> false,
            "tabSize"                   -> 2,
            "indentWithTabs"            -> false,
            "theme"                     -> s"solarized ${settings.theme}",
            "smartIndent"               -> true,
            "keyMap"                    -> "sublime",
            "scrollPastEnd"             -> true,
            "scrollbarStyle"            -> "simple",
            "autoCloseBrackets"         -> true,
            "matchBrackets"             -> true,
            "showCursorWhenSelecting"   -> true,
            "autofocus"                 -> true,
            "highlightSelectionMatches" -> js.Dictionary("showToken" -> js.Dynamic.global.RegExp("\\w")),
            "extraKeys"                 -> js.Dictionary("Tab" -> "defaultTab")
          ).asInstanceOf[codemirror.Options]


          val editor0 = codemirror.CodeMirror.fromTextArea(textArea, options)

          editor0.getDoc.setValue(settings.value)

          editor0.onChange((_, _) =>
            handler.onChange(editor0.getDoc().getValue()).runNow
          )

          editor0
        }.toOption

        scope.modState(_.copy(editor = editor))
      }
    }
    def stop() = {
      scope.modState{s =>
        s.editor.map(_.toTextArea())
        s.copy(editor = None)
      }
    }
  }

  private val component = ReactComponentB[(Settings, Handler)]("CodeMirrorEditor")
    .initialState(State())
    .backend(new Backend(_))
    .renderPS{ case (scope, (props, handler), _) =>
      textarea(
        defaultValue := "",
        onChange ==> handler.onChange,
        ref := texteareaRef,
        autoComplete := "off"
      )
    }
    .componentDidMount(_.backend.start())
    .componentWillUnmount(_.backend.stop())
    .build

  def apply(props: Settings, handler: Handler) = component((props, handler))
}
