package client

import api.{Instrumentation, Value, Html}

import japgolly.scalajs.react._, vdom.all._

import org.scalajs.dom.raw.{
  Element,
  HTMLTextAreaElement,
  HTMLElement,
  HTMLPreElement,
  HTMLDivElement
}
import org.scalajs.dom

import codemirror.{
  Position => CMPosition,
  TextAreaEditor,
  LineWidget,
  CodeMirror,
  Editor => CodeMirrorEditor2,
  TextMarker,
  TextMarkerOptions
}

import scala.scalajs._

object Editor {

  val codemirrorTextarea = Ref[HTMLTextAreaElement]("codemirror-textarea")

  private def options(dark: Boolean): codemirror.Options = {

    val theme = if (dark) "dark" else "light"

    val isMac = dom.window.navigator.userAgent.contains("Mac")
    val ctrl  = if (isMac) "Cmd" else "Ctrl"

    js.Dictionary[Any](
        "mode"                    -> "text/x-scala",
        "autofocus"               -> true,
        "lineNumbers"             -> true,
        "lineWrapping"            -> false,
        "tabSize"                 -> 2,
        "indentWithTabs"          -> false,
        "theme"                   -> s"solarized $theme",
        "smartIndent"             -> true,
        "keyMap"                  -> "sublime",
        "scrollPastEnd"           -> true,
        "scrollbarStyle"          -> "simple",
        "autoCloseBrackets"       -> true,
        "matchBrackets"           -> true,
        "showCursorWhenSelecting" -> true,
        "autofocus"               -> true,
        "highlightSelectionMatches" -> js.Dictionary(
          "showToken" -> js.Dynamic.global.RegExp("\\w")),
        "extraKeys" -> js.Dictionary(
          "Tab"           -> "defaultTab",
          ctrl + "-L"     -> null,
          ctrl + "-l"     -> null,
          ctrl + "-Enter" -> "run",
          "Esc"           -> "clear",
          "F1"            -> "help",
          "F2"            -> "solarizedToggle"
        )
      )
      .asInstanceOf[codemirror.Options]
  }

  private[Editor] sealed trait Annotation {
    def clear(): Unit
  }

  private[Editor] case class Line(lw: LineWidget) extends Annotation {
    def clear() = lw.clear()
  }

  private[Editor] case class Marked(tm: TextMarker) extends Annotation {
    def clear() = tm.clear()
  }

  private[Editor] case class EditorState(
      editor: Option[TextAreaEditor] = None,
      problemAnnotations: Map[api.Problem, Annotation] = Map(),
      renderAnnotations: Map[api.Instrumentation, Annotation] = Map()
  )

  private[Editor] class Backend(
      scope: BackendScope[(App.State, App.Backend), EditorState]) {
    def stop() = {
      scope.modState { s =>
        s.editor.map(_.toTextArea())
        s.copy(editor = None)
      }
    }
    def start() = {
      scope.props.flatMap {
        case (props, backend) =>
          val editor = codemirror.CodeMirror
            .fromTextArea(codemirrorTextarea(scope).get, options(props.dark))

          editor.onChange((_, _) =>
            backend.codeChange(editor.getDoc().getValue()).runNow)

          CodeMirror.commands.run = (editor: CodeMirrorEditor2) => {
            backend.run().runNow
          }

          CodeMirror.commands.clear = (editor: CodeMirrorEditor2) => {
            backend.clear().runNow
          }

          CodeMirror.commands.solarizedToggle =
            (editor: CodeMirrorEditor2) => {
              backend.toogleTheme().runNow
            }

          scope.modState(_.copy(editor = Some(editor)))
      }
    }
  }

  type Scope = CompScope.DuringCallbackM[(App.State, App.Backend),
                                         EditorState,
                                         Backend,
                                         Element]

  private def runDelta(editor: TextAreaEditor,
                       scope: Scope,
                       state: EditorState,
                       current: App.State,
                       next: App.State): Callback = {
    def setTheme() = {
      if (current.dark != next.dark) {
        val theme =
          if (next.dark) "dark"
          else "light"

        editor.setOption("theme", s"solarized $theme")
      }
    }

    def setCode() = {
      if (current.inputs.code != next.inputs.code) {
        val doc = editor.getDoc()
        if (doc.getValue() != next.inputs.code) {
          val cursor = doc.getCursor()
          doc.setValue(next.inputs.code)
          doc.setCursor(cursor)
        }
      }
    }

    val nl        = '\n'
    val modeScala = "text/x-scala"

    def noop[T](v: T): Unit = ()

    def setRenderAnnotations() = {
      val doc = editor.getDoc()
      def nextline2(endPos: CMPosition,
                    node: HTMLElement,
                    process: (HTMLElement => Unit) = noop,
                    options: js.Any = null): Annotation = {
        process(node)
        Line(editor.addLineWidget(endPos.line, node, options))
      }

      def nextline(endPos: CMPosition,
                   content: String,
                   process: (HTMLElement => Unit) = noop,
                   options: js.Any = null): Annotation = {
        val node =
          dom.document.createElement("pre").asInstanceOf[HTMLPreElement]
        node.className = "line"
        node.innerHTML = content

        nextline2(endPos, node, process, options)
      }

      def fold(startPos: CMPosition,
               endPos: CMPosition,
               content: String,
               process: (HTMLElement => Unit) = noop): Annotation = {
        val node =
          dom.document.createElement("div").asInstanceOf[HTMLDivElement]
        node.className = "fold"
        node.innerHTML = content
        process(node)
        Marked(
          doc.markText(startPos,
                       endPos,
                       js.Dictionary[Any]("replacedWith" -> node)
                         .asInstanceOf[TextMarkerOptions]))
      }
      def inline(startPos: CMPosition,
                 content: String,
                 process: (HTMLElement => Unit) = noop): Annotation = {
        // inspired by blink/devtools WebInspector.JavaScriptSourceFrame::_renderDecorations

        val basePos = new CMPosition { line = startPos.line; ch = 0 }
        val offsetPos = new CMPosition {
          line = startPos.line; ch = doc.getLine(startPos.line).length
        }

        val mode   = "local"
        val base   = editor.cursorCoords(basePos, mode)
        val offset = editor.cursorCoords(offsetPos, mode)

        val node =
          dom.document.createElement("pre").asInstanceOf[HTMLPreElement]
        node.className = "inline"
        node.style.left = (offset.left - base.left) + "px"
        node.innerHTML = content
        process(node)

        Line(editor.addLineWidget(startPos.line, node, null))
      }

      setAnnotations[api.Instrumentation](
        _.outputs.instrumentations, {
          case instrumentation @ Instrumentation(api.Position(start, end),
                                                 Value(value, tpe)) => {
            val startPos = doc.posFromIndex(start)
            val endPos   = doc.posFromIndex(end)

            val process = (node: HTMLElement) => {
              CodeMirror.runMode(s"$value: $tpe", modeScala, node)
              node.title = tpe
              ()
            }
            if (value.contains(nl)) nextline(endPos, value, process)
            else inline(startPos, value, process)
          }
          case instrumentation @ Instrumentation(api.Position(start, end),
                                                 Html(content, folded)) => {
            val startPos = doc.posFromIndex(start)
            val endPos   = doc.posFromIndex(end)

            val process: (HTMLElement => Unit) = _.innerHTML = content
            if (!folded) nextline(endPos, content, process)
            else fold(startPos, endPos, content, process)
          }
        },
        _.renderAnnotations,
        f =>
          state => state.copy(renderAnnotations = f(state.renderAnnotations))
      )

    }

    def setProblemAnnotations() = {
      val doc = editor.getDoc()
      setAnnotations[api.Problem](
        _.outputs.compilationInfos,
        info => {
          val line = info.line.getOrElse(0)

          val icon =
            dom.document.createElement("i").asInstanceOf[HTMLDivElement]

          val iconSeverity =
            info.severity match {
              case api.Info    => "info"
              case api.Warning => "warning"
              case api.Error   => "circle-x"
            }

          val classSeverity =
            info.severity match {
              case api.Info    => "info"
              case api.Warning => "warning"
              case api.Error   => "error"
            }

          icon.setAttribute("data-glyph", iconSeverity)
          icon.className = "oi"

          val el =
            dom.document.createElement("div").asInstanceOf[HTMLDivElement]
          el.className = s"compilation-info $classSeverity"

          val msg = dom.document.createElement("pre")
          msg.textContent = info.message

          el.appendChild(icon)
          el.appendChild(msg)

          Line(doc.addLineWidget(line - 1, el))
        },
        _.problemAnnotations,
        f =>
          state => state.copy(problemAnnotations = f(state.problemAnnotations))
      )
    }

    def setAnnotations[T](fromState: App.State => Set[T],
                          annotate: T => Annotation,
                          fromEditorState: EditorState => Map[T, Annotation],
                          updateEditorState: (Map[T, Annotation] => Map[
                                                T,
                                                Annotation]) => EditorState => EditorState)
      : Callback = {

      val added = fromState(next) -- fromState(current)
      val toAdd = CallbackTo
        .sequence(added.map(item => CallbackTo((item, annotate(item)))))
        .map(_.toMap)

      val removed = fromState(current) -- fromState(next)
      val toRemove = CallbackTo.sequence(
        fromEditorState(state)
          .filterKeys(removed.contains)
          .map {
            case (item, annot) => CallbackTo({ annot.clear(); item })
          }
          .toList
      )

      for {
        added   <- toAdd
        removed <- toRemove
        _ <- scope.modState(
          updateEditorState(items => (items ++ added) -- removed))
      } yield ()
    }

    def refresh(): Unit = {
      editor.refresh()
    }

    Callback(setTheme()) >>
      Callback(setCode()) >>
      setProblemAnnotations() >>
      setRenderAnnotations() >>
      Callback(refresh())
  }

  val component = ReactComponentB[(App.State, App.Backend)]("CodemirrorEditor")
    .initialState(EditorState())
    .backend(new Backend(_))
    .renderPS {
      case (scope, (props, backend), _) =>
        textarea(defaultValue := props.inputs.code,
                 onChange ==> backend.codeChange,
                 ref := codemirrorTextarea,
                 autoComplete := "off")
    }
    .componentWillReceiveProps { v =>
      val (current, _) = v.currentProps
      val (next, _)    = v.nextProps
      val state        = v.currentState
      val scope        = v.$

      state.editor
        .map(editor => runDelta(editor, scope, state, current, next))
        .getOrElse(Callback(()))
    }
    .componentDidMount(_.backend.start())
    .componentWillUnmount(_.backend.stop())
    .build

  def apply(state: App.State, backend: App.Backend) =
    component((state, backend))
}
