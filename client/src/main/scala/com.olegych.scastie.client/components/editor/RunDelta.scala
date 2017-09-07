package com.olegych.scastie.client.components.editor

import com.olegych.scastie.client.components._
import com.olegych.scastie.api
import com.olegych.scastie.client.AnsiColorFormatter

import japgolly.scalajs.react.{Callback, CallbackTo}
import japgolly.scalajs.react.extra.Reusability

import codemirror.{
  Hint,
  HintConfig,
  CodeMirror,
  TextAreaEditor,
  TextMarkerOptions,
}
import codemirror.CodeMirror.{Pos => CMPosition}

import org.scalajs.dom.raw.{HTMLElement, HTMLDivElement, HTMLPreElement}
import org.scalajs.dom
import org.scalajs.dom.console

import scala.scalajs.js

private[editor] object RunDelta {

  val editorShouldRefresh: Reusability[Editor] = {
    import EditorReusability._

    Reusability.byRef ||
    (
      Reusability.by((_: Editor).attachedDoms) &&
      Reusability.by((_: Editor).instrumentations) &&
      Reusability.by((_: Editor).compilationInfos) &&
      Reusability.by((_: Editor).runtimeError) &&
      Reusability.by((_: Editor).completions)
    )
  }

  def apply(editor: TextAreaEditor,
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
          val prevScrollPosition = editor.getScrollInfo()
          doc.setValue(next.code)
          editor.scrollTo(prevScrollPosition.left, prevScrollPosition.top)
        }
      }
    }

    val nl = '\n'
    val modeScala = "text/x-scala"

    val doc = editor.getDoc()

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
        doc.markText(
          startPos,
          endPos,
          js.Dictionary[Any](
              "replacedWith" -> node,
              "handleMouseEvents" -> true
            )
            .asInstanceOf[TextMarkerOptions]
        )
      )
    }

    def setRenderAnnotations() = {
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
            case Some(line) =>
              val basePos = new CMPosition { line = lineNumber; ch = 0 }
              val offsetPos = new CMPosition {
                line = lineNumber
                ch = doc2.getLine(lineNumber).map(_.length).getOrElse(0)
              }
              val mode = "local"
              val base = editor2.cursorCoords(basePos, mode)
              val offset = editor2.cursorCoords(offsetPos, mode)
              node.style.left = (offset.left - base.left) + "px"
            case _ =>
              // the line was deleted
              node.innerHTML = null
          }
        }
        updateLeft(editor)
        editor.onChange((editor, _) => updateLeft(editor))

        node.innerHTML = content
        process(node)

        Line(editor.addLineWidget(startPos.line, node, null))
      }

      setAnnotations[api.Instrumentation](
        (props, _) => props.instrumentations, {
          case api.Instrumentation(api.Position(start, end),
                                   api.Value(value, tpe)) =>
            val startPos = doc.posFromIndex(start)
            val endPos = doc.posFromIndex(end)

            val process = (node: HTMLElement) => {
              CodeMirror.runMode(s"$value: $tpe", modeScala, node)
              node.title = tpe
              ()
            }
            if (value.contains(nl)) nextline(endPos, value, process)
            else inline(startPos, value, process)
          case api.Instrumentation(api.Position(start, end),
                                   api.Html(content, folded)) => {

            val startPos = doc.posFromIndex(start)
            val endPos = doc.posFromIndex(end)

            val process: (HTMLElement => Unit) = _.innerHTML = content
            if (!folded) nextline(endPos, content, process)
            else fold(startPos, endPos, content, process)
          }
          case instrumentation @ api.Instrumentation(
                api.Position(start, end),
                api.AttachedDom(uuid, folded)
              ) => {

            val startPos = doc.posFromIndex(start)
            val endPos = doc.posFromIndex(end)

            val domNode = next.attachedDoms.get(uuid)

            if (!domNode.isEmpty) {
              val process: (HTMLElement => Unit) = element => {
                domNode.foreach(element.appendChild)
                ()
              }

              if (!folded) nextline(endPos, "", process)
              else fold(startPos, endPos, "", process)

            } else {
              console.log("cannot find dom element uuid: " + uuid)
              Empty
            }
          }
        },
        _.renderAnnotations,
        (state, annotations) => state.copy(renderAnnotations = annotations)
      )

    }

    def setProblemAnnotations() = {
      val doc = editor.getDoc()
      setAnnotations[api.Problem](
        (props, _) => props.compilationInfos,
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
        (state, annotations) => state.copy(problemAnnotations = annotations)
      )
    }

    def setRuntimeErrorAnnotations(): Callback = {
      val doc = editor.getDoc()
      setAnnotations[api.RuntimeError](
        (props, _) => props.runtimeError.toSet,
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
        (state, annotations) =>
          state.copy(runtimeErrorAnnotations = annotations)
      )
    }

    def setAnnotations[T](
        fromPropsAndState: (Editor, EditorState) => Set[T],
        annotate: T => Annotation,
        fromState: EditorState => Map[T, Annotation],
        updateState: (EditorState, Map[T, Annotation]) => EditorState
    ): Callback = {

      val currentAnnotations: Set[T] =
        current.map(props => fromPropsAndState(props, state)).getOrElse(Set())

      val nextAnnotations: Set[T] =
        fromPropsAndState(next, state)

      val addedAnnotations: Set[T] =
        nextAnnotations -- currentAnnotations

      val annotationsToAdd: CallbackTo[Map[T, Annotation]] =
        CallbackTo
          .sequence(
            addedAnnotations.map(item => CallbackTo((item, annotate(item))))
          )
          .map(_.toMap)

      val removedAnnotations: Set[T] =
        currentAnnotations -- nextAnnotations

      val annotationsToRemove: CallbackTo[Set[T]] =
        CallbackTo.sequence(
          fromState(state)
            .filterKeys(removedAnnotations.contains)
            .map {
              case (item, annot) => CallbackTo({ annot.clear(); item })
            }
            .toSet
        )

      for {
        added <- annotationsToAdd
        removed <- annotationsToRemove
        _ <- modState { state =>
          updateState(state, (fromState(state) ++ added) -- removed)
        }
      } yield ()
    }

    def setCompletions(): Unit = {
      if (state.completionState == Requested ||
          state.completionState == NeedRender ||
          !next.completions.equals(current.getOrElse(next).completions)) {

        state.loadingMessage.hide()

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

        if (enteredBrackets || next.completions.isEmpty) {
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
                      HintConfig
                        .className("autocomplete")
                        .text(completion.hint)
                        .render(
                          (el, _, _) ⇒ {

                            val hint = dom.document
                              .createElement("span")
                              .asInstanceOf[HTMLPreElement]
                            hint.className = "name cm-def"
                            hint.textContent = completion.hint

                            val signature = dom.document
                              .createElement("pre")
                              .asInstanceOf[HTMLPreElement]
                            signature.className = "signature"

                            CodeMirror.runMode(completion.signature,
                                               modeScala,
                                               signature)

                            val resultType = dom.document
                              .createElement("pre")
                              .asInstanceOf[HTMLPreElement]
                            resultType.className = "result-type"

                            CodeMirror.runMode(completion.resultType,
                                               modeScala,
                                               resultType)

                            el.appendChild(hint)
                            el.appendChild(signature)
                            el.appendChild(resultType)

                            if (next.isPresentationMode) {
                              val hintsDiv =
                                signature.parentElement.parentElement
                              hintsDiv.className = hintsDiv.className
                                .concat(" presentation-mode")
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
            next.clearCompletions.runNow()
          }
        }
      }
    }

    def setTypeAt: Callback = {
      if (current.map(_.typeAtInfo) != Some(next.typeAtInfo)) {
        if (next.typeAtInfo.isDefined) {
          state.hoverMessage.updateMessage(next.typeAtInfo.get.typeInfo)
        }
        modState(_.copy(typeAt = next.typeAtInfo))
      } else {
        Callback(())
      }
    }

    def findFolds(code: String): Set[RangePosititon] = {
      val (folds, _, _) = {
        val lines = code.split("\n").toList

        lines.foldLeft((Set.empty[RangePosititon], Option.empty[Int], 0)) {
          case ((folds, open, indexTotal), line) => {
            val (folds0, open0) =
              if (line == "// fold") {
                if (open.isEmpty) (folds, Some(indexTotal))
                else (folds, open)
              } else if (line == "// end-fold") {
                open match {
                  case Some(start) =>
                    (folds + RangePosititon(start, indexTotal + line.length),
                     None)

                  case None => (folds, None)
                }
              } else {
                (folds, open)
              }

            (folds0, open0, indexTotal + line.length + 1)
          }
        }
      }

      folds
    }

    def setCodeFoldingAnnotations(): Callback = {
      val codeChanged = //false
        current.map(_.code != next.code).getOrElse(true)

      setAnnotations[RangePosititon](
        (props, state) => {
          if (current.contains(props)) {
            // code folds are already calculated
            state.codeFoldsAnnotations.keySet
          } else {
            findFolds(props.code) -- state.unfoldedCode
          }
        },
        range => {
          val posStart = doc.posFromIndex(range.indexStart)
          val posEnd = doc.posFromIndex(range.indexEnd)

          val noop: (HTMLElement => Unit) = element => {
            element.onclick = (event: dom.MouseEvent) => {

              // TODO

              // direct.modState(state =>
              //   state.copy(
              //     unfoldedCode = state.unfoldedCode + range
              //   )
              // )
            }
          }

          fold(posStart, posEnd, "", noop)
        },
        _.codeFoldsAnnotations,
        (state, annotations) => state.copy(codeFoldsAnnotations = annotations)
      ).when_(codeChanged)

    }

    def refresh(): Unit = {
      val shouldRefresh =
        current.map(c => !editorShouldRefresh.test(c, next)).getOrElse(true)

      if (shouldRefresh) {
        editor.refresh()
      }
    }

    Callback(setTheme()) >>
      Callback(setCode()) >>
      Callback(setLineNumbers()) >>
      setProblemAnnotations() >>
      setRenderAnnotations() >>
      setRuntimeErrorAnnotations >>
      setCodeFoldingAnnotations() >>
      Callback(setCompletions()) >>
      setTypeAt >>
      Callback(refresh())
  }
}
