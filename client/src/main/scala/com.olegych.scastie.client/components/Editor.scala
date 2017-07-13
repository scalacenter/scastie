package com.olegych.scastie
package client
package components

import codemirror.{
  CodeMirror,
  Hint,
  HintConfig,
  LineWidget,
  TextAreaEditor,
  TextMarker,
  TextMarkerOptions,
  Editor => CodeMirrorEditor2,
  Position => CMPosition
}
import com.olegych.scastie.api._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.{
  HTMLDivElement,
  HTMLElement,
  HTMLPreElement,
  HTMLTextAreaElement
}

import scala.scalajs._

final case class Editor(isDarkTheme: Boolean,
                        isPresentationMode: Boolean,
                        showLineNumbers: Boolean,
                        code: String,
                        attachedDoms: AttachedDoms,
                        instrumentations: Set[Instrumentation],
                        compilationInfos: Set[Problem],
                        runtimeError: Option[RuntimeError],
                        completions: List[Completion],
                        run: Callback,
                        saveOrUpdate: Callback,
                        newSnippet: Callback,
                        clear: Callback,
                        toggleConsole: Callback,
                        toggleWorksheetMode: Callback,
                        toggleTheme: Callback,
                        toggleLineNumbers: Callback,
                        togglePresentationMode: Callback,
                        formatCode: Callback,
                        codeChange: String => Callback,
                        completeCodeAt: Int => Callback,
                        requestTypeAt: (String, Int) => Callback,
                        typeAtInfo: Option[TypeInfoAt],
                        clearCompletions: Callback) {
  @inline def render: VdomElement = Editor.component(this)
}

object Editor {

  // CodeMirror.keyMap.sublime.delete("Ctrl-L")

  private var codemirrorTextarea: HTMLTextAreaElement = _

  private def options(dark: Boolean,
                      showLineNumbers: Boolean): codemirror.Options = {

    val theme = if (dark) "dark" else "light"
    val ctrl = if (View.isMac) "Cmd" else "Ctrl"

    js.Dictionary[Any](
        "mode" -> "text/x-scala",
        "autofocus" -> true,
        "lineNumbers" -> showLineNumbers,
        "gutters" -> js.Array("CodeMirror-linenumbers"),
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
        "highlightSelectionMatches" -> js
          .Dictionary("showToken" -> js.Dynamic.global.RegExp("\\w")),
        "extraKeys" -> js.Dictionary(
          "Tab" -> "defaultTab",
          ctrl + "-Enter" -> "run",
          ctrl + "-S" -> "save",
          ctrl + "-M" -> "newSnippet",
          "Ctrl" + "-Space" -> "autocomplete",
          "Esc" -> "clear",
          "F1" -> "help",
          "F2" -> "toggleSolarized",
          "F3" -> "toggleConsole",
          "F4" -> "toggleWorksheet",
          "F6" -> "formatCode",
          "F7" -> "toggleLineNumbers",
          "F8" -> "togglePresentationMode"
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

  /**
   *    +------------------------+-------------+
   *    v                        |             |
   *   Idle --> Requested --> Active <--> NeedRender
   *    ^           |
   *    +-----------+
   *   only if exactly one
   *   completion returned
   */
  private[Editor] sealed trait CompletionState
  private[Editor] case object Idle extends CompletionState
  private[Editor] case object Requested extends CompletionState
  private[Editor] case object Active extends CompletionState
  private[Editor] case object NeedRender extends CompletionState

  private[Editor] case class EditorState(
      editor: Option[TextAreaEditor] = None,
      problemAnnotations: Map[api.Problem, Annotation] = Map(),
      renderAnnotations: Map[api.Instrumentation, Annotation] = Map(),
      runtimeErrorAnnotations: Map[api.RuntimeError, Annotation] = Map(),
      completionState: CompletionState = Idle,
      showTypeButtonPressed: Boolean = false,
      typeAt: Option[TypeInfoAt] = None
  )

  private[Editor] class EditorBackend(
      scope: BackendScope[Editor, EditorState]
  ) {

    def codeChangeF(event: ReactEventFromInput): Callback =
      scope.props.flatMap(
        _.codeChange(event.target.value)
      )

    def stop(): Callback = {
      scope.modState { s =>
        s.editor.map(_.toTextArea())
        s.copy(editor = None)
      }
    }

    def start(): Callback = {
      scope.props.flatMap { props =>
        val editor =
          codemirror.CodeMirror.fromTextArea(
            codemirrorTextarea,
            options(props.isDarkTheme, props.showLineNumbers)
          )

        editor.onFocus(_.refresh())

        editor.onChange(
          (_, _) => props.codeChange(editor.getDoc().getValue()).runNow()
        )

        // don't show completions if cursor moves to some other place
        editor.onMouseDown(
          (_, _) => scope.modState(_.copy(completionState = Idle)).runNow()
        )

        editor.onKeyUp((_, e) => {
          scope
            .modState(
              s =>
                s.copy(
                  showTypeButtonPressed = s.showTypeButtonPressed && e.keyCode != KeyCode.Ctrl
              )
            )
            .runNow()
        })

        val terminateKeys = Set(
          KeyCode.Space,
          KeyCode.Escape,
          KeyCode.Enter,
          KeyCode.Up,
          KeyCode.Down
        )

        editor.onKeyDown(
          (_, e) => {
            scope
              .modState(
                s => {
                  var resultState = s

                  resultState = resultState
                    .copy(showTypeButtonPressed = e.keyCode == KeyCode.Ctrl)

                  // if any of these keys are pressed
                  // then user doesn't need completions anymore
                  if (terminateKeys.contains(e.keyCode)) {
                    resultState = resultState.copy(completionState = Idle)
                  }

                  // we still show completions but user pressed a key,
                  // => render completions again (filters may apply there)
                  if (resultState.completionState == Active) {
                    // we might need to fetch new completions
                    // when user goes backwards
                    if (e.keyCode == KeyCode.Backspace) {
                      val doc = editor.getDoc()
                      val pos = doc.indexFromPos(doc.getCursor()) - 1
                      props.completeCodeAt(pos).runNow()
                    }

                    resultState = resultState.copy(completionState = NeedRender)
                  }

                  resultState
                }
              )
              .runNow()
          }
        )

        CodeMirror.on(
          editor.getWrapperElement(),
          "mouseover",
          (e: dom.MouseEvent) => {

            def initEventHandlers(node: js.Dynamic, message: HTMLElement) = {

              val tooltip =
                dom.document.createElement("div").asInstanceOf[HTMLDivElement]
              node.className = node.className.concat(" CodeMirror-hover")
              tooltip.className =
                tooltip.className.concat(" CodeMirror-hover-tooltip")
              tooltip.appendChild(message)
              dom.document.body.appendChild(tooltip)

              def remove(element: HTMLElement) = {
                if (element.parentNode != null) {
                  element.parentNode.removeChild(element)
                }
              }

              def hideTooltip(e: dom.Event): Unit = {
                CodeMirror.off(dom.document, "mouseout", hideTooltip)
                node.className = node.className.replace(" CodeMirror-hover", "")

                if (tooltip.parentNode != null) {
                  if (tooltip.style.opacity == null) {
                    remove(tooltip)
                  }
                  tooltip.style.opacity = "0"
                  dom.window.setTimeout(() => {
                    remove(tooltip)
                  }, 600)
                  ()
                }
              }

              def position(e: dom.MouseEvent): Unit = {
                if (tooltip.parentNode == null) {
                  CodeMirror.off(dom.document, "mousemove", position)
                }
                tooltip.style.top = Math
                  .max(0, e.clientY - tooltip.offsetHeight - 5) + "px"
                tooltip.style.left = (e.clientX + 5) + "px"
              }

              CodeMirror.on(dom.document, "mousemove", position)
              CodeMirror.on(dom.document, "mouseout", hideTooltip)
              position(e)
              if (tooltip.style.opacity != null) {
                tooltip.style.opacity = "1"
              }
            }

            val node = e.target
            if (node != null && node.isInstanceOf[HTMLElement]) {
              val text = node.asInstanceOf[HTMLElement].textContent
              if (text != null) {

                // request token under the cursor
                val pos = editor.coordsChar(
                  js.Dictionary[Any](
                    "left" -> e.clientX,
                    "top" -> e.clientY
                  ),
                  mode = null
                )
                val currToken = editor.getTokenAt(pos, precise = null).string

                // Request type info only if Ctrl is pressed
                if (currToken == text) {
                  val s = scope.state.runNow()
                  if (s.showTypeButtonPressed) {
                    val message = dom.document
                      .createElement("div")
                      .asInstanceOf[HTMLDivElement]

                    val lastTypeInfo = s.typeAt
                    if (lastTypeInfo.isEmpty || lastTypeInfo.get.token != currToken) {
                      // if it's the first typeAt request
                      // OR if user's moved on to a new token
                      // then we request new type information with curr token and show "..."
                      props
                        .requestTypeAt(currToken,
                                       editor.getDoc().indexFromPos(pos))
                        .runNow()
                      message.innerHTML = "..."
                    } else {
                      message.innerHTML = s.typeAt.get.typeInfo
                    }
                    initEventHandlers(node.asInstanceOf[js.Dynamic], message)
                  }
                }
              }
            }
          }
        )

        CodeMirror.commands.run = (editor: CodeMirrorEditor2) => {
          props.run.runNow()
        }

        CodeMirror.commands.save = (editor: CodeMirrorEditor2) => {
          props.saveOrUpdate.runNow()
        }

        CodeMirror.commands.newSnippet = (editor: CodeMirrorEditor2) => {
          props.newSnippet.runNow()
        }

        CodeMirror.commands.clear = (editor: CodeMirrorEditor2) => {
          props.clear.runNow()
        }

        CodeMirror.commands.toggleConsole = (editor: CodeMirrorEditor2) => {
          props.toggleConsole.runNow()
        }

        CodeMirror.commands.toggleWorksheet = (editor: CodeMirrorEditor2) => {
          props.toggleWorksheetMode.runNow()
        }

        CodeMirror.commands.toggleSolarized = (editor: CodeMirrorEditor2) => {
          props.toggleTheme.runNow()
        }

        CodeMirror.commands.formatCode = (editor: CodeMirrorEditor2) => {
          props.formatCode.runNow()
        }

        CodeMirror.commands.toggleLineNumbers = (editor: CodeMirrorEditor2) => {
          props.toggleLineNumbers.runNow()
        }

        CodeMirror.commands.togglePresentationMode =
          (editor: CodeMirrorEditor2) => {
            props.togglePresentationMode.runNow()
          }

        CodeMirror.commands.autocomplete = (editor: CodeMirrorEditor2) => {
          val doc = editor.getDoc()
          val pos = doc.indexFromPos(doc.getCursor())

          props.completeCodeAt(pos).runNow()
          scope.modState(_.copy(completionState = Requested)).runNow()
        }

        val setEditor =
          scope.modState(_.copy(editor = Some(editor)))

        val applyDeltas =
          scope.state.flatMap(
            state =>
              runDelta(editor, (f => scope.modState(f)), state, None, props)
          )

        setEditor >> applyDeltas
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
                       current: Option[Editor],
                       next: Editor): Callback = {
    def setTheme() = {
      if (current.map(_.isDarkTheme) != Some(next.isDarkTheme)) {
        val theme =
          if (next.isDarkTheme) "dark"
          else "light"

        editor.setOption("theme", s"solarized $theme")
      }
    }

    def setLineNumbers() = {
      if (current.map(_.showLineNumbers) != Some(next.showLineNumbers)) {
        editor.setOption("lineNumbers", next.showLineNumbers)
      }
    }

    def setCode() = {
      if (current.map(_.code) != Some(next.code)) {
        val doc = editor.getDoc()
        if (doc.getValue() != next.code) {
          setCode2(editor, next.code)
        }
      }
    }

    val nl = '\n'
    val modeScala = "text/x-scala"

    def setRenderAnnotations() = {
      val doc = editor.getDoc()
      def nextline2(endPos: CMPosition,
                    node: HTMLElement,
                    process: (HTMLElement => Unit),
                    options: js.Any): Annotation = {
        process(node)
        Line(editor.addLineWidget(endPos.line, node, options))
      }

      def nextline(endPos: CMPosition,
                   content: String,
                   process: (HTMLElement => Unit),
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
               process: (HTMLElement => Unit)): Annotation = {
        val node =
          dom.document.createElement("div").asInstanceOf[HTMLDivElement]
        node.className = "fold"
        node.innerHTML = content
        process(node)
        Marked(
          doc.markText(startPos,
                       endPos,
                       js.Dictionary[Any]("replacedWith" -> node)
                         .asInstanceOf[TextMarkerOptions])
        )
      }
      def inline(startPos: CMPosition,
                 content: String,
                 process: (HTMLElement => Unit)): Annotation = {
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
        _.instrumentations, {
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
                AttachedDom(uuid, folded)
              ) => {

            val startPos = doc.posFromIndex(start)
            val endPos = doc.posFromIndex(end)

            val domNode =
              next.attachedDoms.get(uuid).getOrElse {
                val node = dom.document
                  .createElement("pre")
                  .asInstanceOf[HTMLPreElement]
                node.innerHTML = "cannot find dom element uuid: " + uuid
                node
              }

            val process: (HTMLElement => Unit) = element => {
              element.appendChild(domNode)
              ()
            }

            if (!folded) nextline(endPos, "", process)
            else fold(startPos, endPos, "", process)
          }
        },
        _.renderAnnotations,
        f => state => state.copy(renderAnnotations = f(state.renderAnnotations))
      )

    }

    def setProblemAnnotations() = {
      val doc = editor.getDoc()
      setAnnotations[api.Problem](
        _.compilationInfos,
        info => {
          val line = info.line.getOrElse(0)

          val icon =
            dom.document.createElement("i").asInstanceOf[HTMLDivElement]

          val iconSeverity =
            info.severity match {
              case api.Info    => "fa fa-info"
              case api.Warning => "fa fa-exclamation-triangle"
              case api.Error   => "fa fa-times-circle"
            }

          val classSeverity =
            info.severity match {
              case api.Info    => "info"
              case api.Warning => "warning"
              case api.Error   => "error"
            }

          icon.className = iconSeverity

          val el =
            dom.document.createElement("div").asInstanceOf[HTMLDivElement]
          el.className = s"compilation-info $classSeverity"

          val msg = dom.document.createElement("pre")

          msg.innerHTML = AnsiColorFormatter.formatToHtml(info.message)

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
        _.runtimeError.toSet,
        runtimeError => {
          val line = runtimeError.line.getOrElse(0)

          val icon =
            dom.document.createElement("i").asInstanceOf[HTMLDivElement]

          icon.className = "fa fa-times-circle"

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
              runtimeErrorAnnotations = f(state.runtimeErrorAnnotations)
        )
      )
    }

    def setAnnotations[T](
        fromState: Editor => Set[T],
        annotate: T => Annotation,
        fromEditorState: EditorState => Map[T, Annotation],
        updateEditorState: (
            Map[T, Annotation] => Map[T, Annotation]
        ) => EditorState => EditorState
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

    def setCompletions(): Unit = {
      if (state.completionState == Requested ||
          state.completionState == NeedRender ||
          !next.completions.equals(current.getOrElse(next).completions)) {
        val doc = editor.getDoc()
        val cursor = doc.getCursor()
        var fr = cursor.ch
        val to = cursor.ch
        val currLine = cursor.line
        val alphaNum = ('a' to 'z').toSet ++ ('A' to 'Z').toSet ++ ('0' to '9').toSet
        val lineContent = doc.getLine(currLine).getOrElse("")

        var i = fr - 1
        while (i >= 0 && alphaNum.contains(lineContent.charAt(i))) {
          fr = i
          i -= 1
        }

        val currPos = doc.indexFromPos(doc.getCursor())
        val filter = doc
          .getValue()
          .substring(doc.indexFromPos(new CMPosition {
            line = currLine; ch = fr
          }), currPos)

        // stop autocomplete if user reached brackets
        val enteredBrackets =
          doc.getValue().substring(currPos - 1, currPos + 1) == "()" &&
            state.completionState != Requested

        if (enteredBrackets) {
          modState(_.copy(completionState = Idle)).runNow()
        } else {
          // autopick single completion only if it's user's first request
          val completeSingle = next.completions.length == 1 && state.completionState == Requested

          CodeMirror.showHint(
            editor,
            (_, options) => {
              js.Dictionary(
                "from" -> new CMPosition {
                  line = currLine; ch = fr
                },
                "to" -> new CMPosition {
                  line = currLine; ch = to
                },
                "list" -> next.completions
                // FIXME: can place not 'important' completions first
                  .filter(_.hint.startsWith(filter))
                  .map {
                    completion =>
                      val hint = completion.hint

                      HintConfig
                        .className("autocomplete")
                        .text(hint)
                        .render(
                          (el, _, _) ⇒ {

                            val node = dom.document
                              .createElement("pre")
                              .asInstanceOf[HTMLPreElement]
                            node.className = "signature"

                            CodeMirror.runMode(completion.typeInfo,
                                               modeScala,
                                               node)

                            val span = dom.document
                              .createElement("span")
                              .asInstanceOf[HTMLPreElement]
                            span.className = "name cm-def"
                            span.textContent = hint

                            el.appendChild(span)
                            el.appendChild(node)

                            if (next.isPresentationMode) {
                              val hintsDiv = node.parentElement.parentElement
                              hintsDiv.className = hintsDiv.className
                                .concat(" CodeMirror-hints-presentation-mode")
                            }
                          }
                        ): Hint
                  }
                  .to[js.Array]
              )
            },
            js.Dictionary(
              "container" -> dom.document.querySelector(".CodeMirror"),
              "alignWithWord" -> true,
              "completeSingle" -> completeSingle
            )
          )

          modState(_.copy(completionState = Active)).runNow()
          if (completeSingle) {
            modState(_.copy(completionState = Idle)).runNow()
          }
        }
      }
    }

    def setTypeAt(): Unit = {
      if (current.map(_.typeAtInfo) != Some(next.typeAtInfo)) {
        modState(_.copy(typeAt = next.typeAtInfo)).runNow()
      }
    }

    def refresh(): Unit = {
      editor.refresh()
    }

    Callback(setTheme()) >>
      Callback(setCode()) >>
      Callback(setLineNumbers()) >>
      setProblemAnnotations() >>
      setRenderAnnotations() >>
      setRuntimeError >>
      Callback(setCompletions()) >>
      Callback(setTypeAt()) >>
      Callback(refresh())
  }

  private val component =
    ScalaComponent
      .builder[Editor]("Editor")
      .initialState(EditorState())
      .backend(new EditorBackend(_))
      .renderPS {
        case (scope, props, _) =>
          textarea.ref(codemirrorTextarea = _)(
            onChange ==> scope.backend.codeChangeF,
            value := props.code,
            name := "CodeArea",
            autoComplete := "off"
          )
      }
      .componentWillReceiveProps { scope =>
        val current = scope.currentProps
        val next = scope.nextProps
        val state = scope.state

        state.editor
          .map(
            editor =>
              runDelta(editor,
                       (f => scope.modState(f)),
                       state,
                       Some(current),
                       next)
          )
          .getOrElse(Callback(()))

      }
      .componentDidMount(_.backend.start())
      .componentWillUnmount(_.backend.stop())
      .build
}
