package client

import japgolly.scalajs.react._, vdom.all._

import org.scalajs.dom.raw.{Element, HTMLTextAreaElement}
import org.scalajs.dom

import codemirror.{TextAreaEditor, LineWidget, CodeMirror, Editor => CodeMirrorEditor}

import scala.scalajs._

object Editor {

  val codemirrorTextarea = Ref[HTMLTextAreaElement]("codemirror-textarea")

  private def options(dark: Boolean): codemirror.Options = {

    val theme = if(dark) "dark" else "light"

    val isMac = dom.window.navigator.userAgent.contains("Mac")
    val ctrl = if(isMac) "Cmd" else "Ctrl"
  
    js.Dictionary[Any](
      "mode"                      -> "text/x-scala",
      "autofocus"                 -> true,
      "lineNumbers"               -> false,
      "lineWrapping"              -> false,
      "tabSize"                   -> 2,
      "indentWithTabs"            -> false,
      "theme"                     -> s"solarized $theme",
      "smartIndent"               -> true,
      "keyMap"                    -> "sublime",
      "scrollPastEnd"             -> true,
      "scrollbarStyle"            -> "simple",
      "autoCloseBrackets"         -> true,
      "matchBrackets"             -> true,
      "showCursorWhenSelecting"   -> true,
      "autofocus"                 -> true,
      "highlightSelectionMatches" -> js.Dictionary("showToken" -> js.Dynamic.global.RegExp("\\w")),
      "extraKeys"                 -> js.Dictionary(
        "Tab"          -> "defaultTab",
        s"$ctrl-l"     -> null,
        s"$ctrl-Enter" -> "run",
        "F1"           -> "help",
        "F2"           -> "solarizedToggle"
      )
    ).asInstanceOf[codemirror.Options]
  }

  private[Editor] sealed trait Annotation { 
    def clear(): Unit 
  }

  private[Editor] case class Line(lw: LineWidget) extends Annotation {
    def clear() = lw.clear()
  }

  // private[Editor] case class Marked(tm: TextMarker) extends Annotation {
  //   def clear() = tm.clear()
  // }

  private[Editor] case class EditorState(
    editor: Option[TextAreaEditor] = None,
    annotations: Map[CompilationInfo, Annotation] = Map()
  )

  private[Editor] class Backend(scope: BackendScope[(App.State, App.Backend), EditorState]) {
    def stop() = {
      scope.modState{s =>
        s.editor.map(_.toTextArea())
        s.copy(editor = None)
      }
    }
    def start() = {
      scope.props.flatMap{ case (props, backend) =>
        val editor = codemirror.CodeMirror.fromTextArea(codemirrorTextarea(scope).get, options(props.dark))

        editor.onChange((_, _) => 
          backend.codeChange(editor.getDoc().getValue()).runNow
        )

        CodeMirror.commands.run = (editor: CodeMirrorEditor) ⇒ {

        }

        CodeMirror.commands.solarizedToggle = (editor: CodeMirrorEditor) ⇒ {
          backend.toogleTheme().runNow
        }

        scope.modState(_.copy(editor = Some(editor)))
      }
    }
  }

  type Scope = CompScope.DuringCallbackM[(App.State, App.Backend), EditorState, Backend, Element]

  private def runDelta(editor: TextAreaEditor, scope: Scope, state: EditorState, current: App.State, next: App.State): Callback = {
    def setTheme() = {
      if(current.dark != next.dark) {
        val theme = 
          if(next.dark) "dark"
          else "light"

        editor.setOption("theme", s"solarized $theme")
      }
    }

    def setCode() = {
      if(current.code != next.code) {
        val doc = editor.getDoc()
        if(doc.getValue() != next.code) {
          val cursor = doc.getCursor()
          doc.setValue(next.code)
          doc.setCursor(cursor)
        }
      }    
    }

    def setAnnotations() = {
      val doc = editor.getDoc()

      val added = next.compilationInfos -- current.compilationInfos

      val toAdd = 
        CallbackTo.sequence(
          added.map{ info =>
            val pos = doc.posFromIndex(info.position.end)
            val el = dom.document.createElement("div")
            el.textContent = info.message

            CallbackTo((info, Line(doc.addLineWidget(pos.line, el))))
          }
        ).map(_.toMap)

      val removed = current.compilationInfos -- next.compilationInfos

      val toRemove = CallbackTo.sequence(
        state.annotations.filterKeys(removed.contains).map{
          case (info, annot) => CallbackTo({annot.clear(); info})
        }.toList
      )
      
      for {
        added   <- toAdd
        removed <- toRemove
        _       <- scope.modState(s => s.copy(annotations = 
                    ((s.annotations ++ added) -- removed)
                   ))
      } yield ()
    }

    Callback(setTheme()) >> Callback(setCode()) >> setAnnotations()
  }

  val component = ReactComponentB[(App.State, App.Backend)]("CodemirrorEditor")
    .initialState(EditorState())
    .backend(new Backend(_))
    .renderPS{ case (scope, (props, backend), _) => 
      textarea(defaultValue := props.code, onChange ==> backend.codeChange, ref := codemirrorTextarea, autoComplete := "off")
    }
    .componentWillReceiveProps{v => 
      val (current, _) = v.currentProps
      val (next, _) = v.nextProps
      val state = v.currentState
      val scope = v.$

      state.editor.map(editor => 
        runDelta(editor, scope, state, current, next)
      ).getOrElse(Callback(()))
    }
    .componentDidMount(_.backend.start())
    .componentWillUnmount(_.backend.stop())
    .build
 
  def apply(state: App.State, backend: App.Backend) = component((state, backend))
}