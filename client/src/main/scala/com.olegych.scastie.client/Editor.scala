package com.olegych.scastie
package client

import api.{Instrumentation, Value, Html, AttachedDom}

import japgolly.scalajs.react._, vdom.all._

import org.scalajs.dom.raw.{
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

  CodeMirror.keyMap.sublime.delete("Ctrl-L")

  private val codemirrorTextarea =
    Ref[HTMLTextAreaElement]("codemirror-textarea")

  private def options(dark: Boolean): codemirror.Options = {

    val theme = if (dark) "dark" else "light"
    val ctrl = if (View.isMac) "Cmd" else "Ctrl"

    js.Dictionary[Any](
        "mode" -> "text/x-scala",
        "autofocus" -> true,
        "lineNumbers" -> false,
        "lineWrapping" -> false,
        "tabSize" -> 2,
        "indentWithTabs" -> false,
        "theme" -> s"solarized $theme",
        "smartIndent" -> true,
        "keyMap" -> "sublime",
        "scrollPastEnd" -> false,
        "scrollbarStyle" -> "simple",
        "autoCloseBrackets" -> true,
        "matchBrackets" -> true,
        "showCursorWhenSelecting" -> true,
        "autofocus" -> true,
        "highlightSelectionMatches" -> js.Dictionary(
          "showToken" -> js.Dynamic.global.RegExp("\\w")),
        "extraKeys" -> js.Dictionary(
          "Tab" -> "defaultTab",
          ctrl + "-Enter" -> "run",
          ctrl + "-S" -> "save",
          "Esc" -> "clear",
          "F1" -> "help",
          "F2" -> "toggleSolarized",
          "F3" -> "toggleConsole",
          "F4" -> "toggleWorksheet",
          "F6" -> "formatCode"
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
      renderAnnotations: Map[api.Instrumentation, Annotation] = Map(),
      runtimeErrorAnnotations: Map[api.RuntimeError, Annotation] = Map()
  )

  private[Editor] class Backend(
      scope: BackendScope[(AppState, AppBackend), EditorState]) {
    def stop() = {
      scope.modState { s =>
        s.editor.map(_.toTextArea())
        s.copy(editor = None)
      }
    }
    def start() = {
      scope.props.flatMap {
        case (props, backend) =>
          val editor =
            codemirror.CodeMirror.fromTextArea(codemirrorTextarea(scope).get,
                                               options(props.isDarkTheme))

          editor.onFocus(_.refresh())

          editor.onChange((_, _) =>
            backend.codeChange(editor.getDoc().getValue()).runNow)

          CodeMirror.commands.run = (editor: CodeMirrorEditor2) => {
            backend.run().runNow
          }

          CodeMirror.commands.save = (editor: CodeMirrorEditor2) => {
            backend.saveOrUpdate().runNow
          }

          CodeMirror.commands.clear = (editor: CodeMirrorEditor2) => {
            backend.clear().runNow
          }

          CodeMirror.commands.toggleConsole = (editor: CodeMirrorEditor2) => {
            backend.toggleConsole().runNow
          }

          CodeMirror.commands.toggleWorksheet =
            (editor: CodeMirrorEditor2) => {
              backend.toggleWorksheetMode().runNow
            }

          CodeMirror.commands.toggleSolarized =
            (editor: CodeMirrorEditor2) => {
              backend.toggleTheme().runNow
            }

          CodeMirror.commands.formatCode = (editor: CodeMirrorEditor2) => {
            backend.formatCode().runNow
          }

          scope.modState(_.copy(editor = Some(editor))) >>
            scope.state.flatMap(state =>
              runDelta(editor, (f => scope.modState(f)), state, None, props))
      }
    }
  }

  private def setCode2(editor: TextAreaEditor, code: String): Unit = {
    val doc = editor.getDoc()
    val prevScrollPosition = editor.getScrollInfo()
    doc.setValue(code)
    editor.scrollTo(prevScrollPosition.left, prevScrollPosition.top)
  }

  private def runDelta(editor: TextAreaEditor,
                       modState: (EditorState => EditorState) => Callback,
                       state: EditorState,
                       current: Option[AppState],
                       next: AppState): Callback = {

    def setTheme() = {
      if (current.map(_.isDarkTheme) != Some(next.isDarkTheme)) {
        val theme =
          if (next.isDarkTheme) "dark"
          else "light"

        editor.setOption("theme", s"solarized $theme")
      }
    }

    def setCode() = {
      if (current.map(_.inputs.code) != Some(next.inputs.code)) {
        val doc = editor.getDoc()
        if (doc.getValue() != next.inputs.code) {
          setCode2(editor, next.inputs.code)
        }
      }
    }

    val nl = '\n'
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

        val node =
          dom.document.createElement("pre").asInstanceOf[HTMLPreElement]

        node.className = "inline"

        def updateLeft(editor2: codemirror.Editor): Unit = {
          val doc2 = editor2.getDoc()
          val lineNumber = startPos.line
          doc2.getLine(lineNumber).toOption match {
            case Some(line) => {

              val basePos = new CMPosition { line = lineNumber; ch = 0 }
              val offsetPos = new CMPosition {
                line = lineNumber;
                ch = doc2.getLine(lineNumber).map(_.length).getOrElse(0)
              }
              val mode = "local"
              val base = editor2.cursorCoords(basePos, mode)
              val offset = editor2.cursorCoords(offsetPos, mode)
              node.style.left = (offset.left - base.left) + "px"

            }
            case _ => {
              // the line was deleted
              node.innerHTML = null
            }
          }
        }
        updateLeft(editor)
        editor.onChange((editor, _) => updateLeft(editor))

        node.innerHTML = content
        process(node)

        Line(editor.addLineWidget(startPos.line, node, null))
      }

      setAnnotations[api.Instrumentation](
        _.outputs.instrumentations, {
          case instrumentation @ Instrumentation(api.Position(start, end),
                                                 Value(value, tpe)) => {
            val startPos = doc.posFromIndex(start)
            val endPos = doc.posFromIndex(end)

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
            val endPos = doc.posFromIndex(end)

            val process: (HTMLElement => Unit) = _.innerHTML = content
            if (!folded) nextline(endPos, content, process)
            else fold(startPos, endPos, content, process)
          }
          case instrumentation @ Instrumentation(
                api.Position(start, end),
                AttachedDom(uuid, folded)) => {

            val startPos = doc.posFromIndex(start)
            val endPos = doc.posFromIndex(end)

            val dom = next.attachedDoms(uuid)

            val process: (HTMLElement => Unit) = element => {
              element.appendChild(dom)
              ()
            }

            if (!folded) nextline(endPos, "", process)
            else fold(startPos, endPos, "", process)
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
              case api.Info => "info"
              case api.Warning => "warning"
              case api.Error => "circle-x"
            }

          val classSeverity =
            info.severity match {
              case api.Info => "info"
              case api.Warning => "warning"
              case api.Error => "error"
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

    def setRuntimeError(): Callback = {
      val doc = editor.getDoc()
      setAnnotations[api.RuntimeError](
        _.outputs.runtimeError.toSet,
        runtimeError => {
          val line = runtimeError.line.getOrElse(0)

          val icon =
            dom.document.createElement("i").asInstanceOf[HTMLDivElement]

          val iconSeverity = "circle-x"
          icon.setAttribute("data-glyph", iconSeverity)
          icon.className = "oi"

          val el =
            dom.document.createElement("div").asInstanceOf[HTMLDivElement]
          el.className = "runtime-error"

          val msg = dom.document.createElement("pre")
          msg.textContent = s"""|${runtimeError.message}
                                |
                                |${runtimeError.fullStack}""".stripMargin

          el.appendChild(icon)
          el.appendChild(msg)

          Line(doc.addLineWidget(line - 1, el))
        },
        _.runtimeErrorAnnotations,
        f =>
          state =>
            state.copy(
              runtimeErrorAnnotations = f(state.runtimeErrorAnnotations)))
    }

    def setAnnotations[T](
        fromState: AppState => Set[T],
        annotate: T => Annotation,
        fromEditorState: EditorState => Map[T, Annotation],
        updateEditorState: (Map[T, Annotation] => Map[T, Annotation]) => EditorState => EditorState
    ): Callback = {

      val added = fromState(next) -- current.map(fromState).getOrElse(Set())
      val toAdd = CallbackTo
        .sequence(added.map(item => CallbackTo((item, annotate(item)))))
        .map(_.toMap)

      val removed = current.map(fromState).getOrElse(Set()) -- fromState(next)
      val toRemove = CallbackTo.sequence(
        fromEditorState(state)
          .filterKeys(removed.contains)
          .map {
            case (item, annot) => CallbackTo({ annot.clear(); item })
          }
          .toList
      )

      for {
        added <- toAdd
        removed <- toRemove
        _ <- modState(updateEditorState(items => (items ++ added) -- removed))
      } yield ()
    }

    def refresh(): Unit = {
      editor.refresh()
    }

    Callback(setTheme()) >>
      Callback(setCode()) >>
      setProblemAnnotations() >>
      setRenderAnnotations() >>
      setRuntimeError >>
      Callback(refresh())
  }

  private val component =
    ReactComponentB[(AppState, AppBackend)]("CodemirrorEditor")
      .initialState(EditorState())
      .backend(new Backend(_))
      .renderPS {
        case (scope, (props, backend), state) =>
          textarea(onChange ==> backend.codeChange,
                   value := props.inputs.code,
                   ref := codemirrorTextarea,
                   name := "CodeArea",
                   autoComplete := "off")
      }
      .componentWillReceiveProps { v =>
        val (current, _) = v.currentProps
        val (next, _) = v.nextProps
        val state = v.currentState
        val scope = v.$

        state.editor
          .map(
            editor =>
              runDelta(editor,
                       (f => scope.modState(f)),
                       state,
                       Some(current),
                       next))
          .getOrElse(Callback(()))
      }
      .componentDidMount(_.backend.start())
      .componentWillUnmount(_.backend.stop())
      .build

  def apply(state: AppState, backend: AppBackend) =
    component((state, backend))
}
