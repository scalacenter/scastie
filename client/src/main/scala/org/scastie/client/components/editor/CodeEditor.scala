package org.scastie.client.components.editor

import org.scastie.api._
import org.scastie.runtime.api._
import org.scastie.client.HTMLFormatter
import org.scastie.client._
import japgolly.scalajs.react._
import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.HTMLElement
import typings.codemirrorAutocomplete.mod._
import typings.codemirrorCommands.mod._
import typings.codemirrorLanguage.mod._
import typings.codemirrorLint.codemirrorLintStrings
import typings.codemirrorLint.mod._
import typings.codemirrorSearch.mod._
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._
import typings.replitCodemirrorEmacs.mod.emacs
import typings.replitCodemirrorVim.mod.vim

import scalajs.js
import vdom.all._
import JsUtils._
import hooks.Hooks.UseStateF
import js.JSConverters._

final case class CodeEditor(visible: Boolean,
                            isDarkTheme: Boolean,
                            isPresentationMode: Boolean,
                            isWorksheetMode: Boolean,
                            isEmbedded: Boolean,
                            editorMode: EditorMode,
                            showLineNumbers: Boolean,
                            value: String,
                            attachedDoms: Map[String, HTMLElement],
                            instrumentations: Set[Instrumentation],
                            compilationInfos: Set[Problem],
                            runtimeError: Option[RuntimeError],
                            saveOrUpdate: Reusable[Callback],
                            clear: Reusable[Callback],
                            openNewSnippetModal: Reusable[Callback],
                            toggleHelp: Reusable[Callback],
                            toggleConsole: Reusable[Callback],
                            toggleLineNumbers: Reusable[Callback],
                            togglePresentationMode: Reusable[Callback],
                            formatCode: Reusable[Callback],
                            codeChange: String ~=> Callback,
                            target: ScalaTarget,
                            metalsStatus: MetalsStatus,
                            setMetalsStatus: MetalsStatus ~=> Callback,
                            dependencies: Set[ScalaDependency])
    extends Editor {
  @inline def render: VdomElement = CodeEditor.hooksComponent(this)
}

object CodeEditor {

  var sharedHighlighter: Option[SyntaxHighlighter] = None

  private def init(
    props: CodeEditor,
    ref: Ref.Simple[Element],
    editorView: UseStateF[CallbackTo, EditorView]
  ): Callback = {

    if(props.editorMode == Vim) {
      EditorKeymaps.registerVimCommands(props)
    }

    ref.foreachCB(divRef => {

      val onHighlighterReady: SyntaxHighlighter => Callback = highlighter =>
        Callback { sharedHighlighter = Some(highlighter) }

      val syntaxHighlighting = new SyntaxHighlightingPlugin(editorView, onHighlighterReady)
      val modeExtension: Extension =
        getExtension(props.editorMode)
      val extensions =
        js.Array[Any](
        Editor.editorTheme.of(props.codemirrorTheme),
        Editor.editorModeCompartment.of(modeExtension),
        lineNumbers(),
        highlightSpecialChars(),
        history(),
        drawSelection(),
        dropCursor(),
        EditorState.allowMultipleSelections.of(true),
        indentOnInput(),
        bracketMatching(),
        closeBrackets(),
        rectangularSelection(),
        crosshairCursor(),
        highlightSelectionMatches(),
        Editor.indentationMarkersExtension,
        keymap.of(closeBracketsKeymap ++ defaultKeymap ++ historyKeymap ++ foldKeymap ++ completionKeymap ++ lintKeymap ++ searchKeymap),
        StateField
          .define(StateFieldSpec[Set[Instrumentation]](_ => props.instrumentations, (value, _) => value))
          .extension,
        DecorationProvider(props),
        EditorState.tabSize.of(2),
        Prec.highest(EditorKeymaps.keymapping(props)),
        InteractiveProvider.interactive.of(InteractiveProvider(props, () => sharedHighlighter).extension),
        SyntaxHighlightingTheme.highlightingTheme,
        lintGutter(),
        OnChangeHandler(props.codeChange),
        syntaxHighlighting.syntaxHighlightingExtension.of(syntaxHighlighting.fallbackExtension)
      )

      val editorStateConfig = EditorStateConfig()
        .setExtensions(extensions)
        .setDoc(props.value)

      val editor = new EditorView(EditorViewConfig()
        .setState(EditorState.create(editorStateConfig))
        .setParent(divRef)
      )

      val initResult = editorView.setState(editor)

      EditorKeymaps.setupGlobalKeybinds(props)

      initResult
    })
  }

  def problemToDiagnostics(problem: Problem, doc: Text): Seq[Diagnostic] = {
    val maxLine = doc.lines.toInt
    val startLine = problem.line.get.max(1).min(maxLine)
    val endLine = problem.endLine.getOrElse(startLine).max(1).min(maxLine)

    val renderMessage = (_: EditorView) => {
      val wrapper = dom.document.createElement("pre")
      wrapper.innerHTML = HTMLFormatter.format(problem.message)
      wrapper
    }

    val actions = problemToActions(problem, doc)

    /* Split multi-line diagnostics into per-line diagnostics for proper tooltip positioning */
    (startLine to endLine).map { lineNum =>
      val lineInfo = doc.line(lineNum)
      val isStartLine = lineNum == startLine
      val isEndLine = lineNum == endLine

      val from = if (isStartLine) {
        problem.startColumn match {
          case Some(col) if col > 0 =>
            val clampedStart = (col min (lineInfo.length.toInt + 1)) max 1
            lineInfo.from + clampedStart - 1
          case _ =>
            lineInfo.from
        }
      } else {
        lineInfo.from
      }

      val to = if (isEndLine) {
        if (problem.line.get > maxLine && isStartLine) {
          lineInfo.to
        } else {
          problem.endColumn match {
            case Some(col) if col > 0 =>
              val clampedEnd = (col min (lineInfo.length.toInt + 1)) max 1
              (lineInfo.from + clampedEnd - 1) min lineInfo.to
            case _ =>
              lineInfo.to
          }
        }
      } else {
        lineInfo.to
      }

      val diagnostic = Diagnostic(from, problem.message, parseSeverity(problem.severity), to)
        .setRenderMessage(renderMessage)
      actions.foreach(a => diagnostic.setActions(a.toJSArray))
      diagnostic
    }
  }

  /* e.g.: line: 5, character: 10 -> offset: 67 */
  private def positionToOffset(line: Int, character: Int, doc: Text): Int = {
    val clampedLine = ((line + 1) min doc.lines.toInt) max 1
    val lineInfo = doc.line(clampedLine)
    (lineInfo.from.toInt + character).min(doc.length.toInt)
  }

  def problemToActions(problem: Problem, doc: Text): Option[List[Action]] = {
    problem.actions.map { scalaActions =>
      scalaActions.map { scalaAction =>
        Action(
          apply = (view: EditorView, _: Double, _: Double) => {
            scalaAction.edit.foreach { workspaceEdit =>
              val changes = workspaceEdit.changes.map { textEdit =>
                val from = positionToOffset(textEdit.range.start.line, textEdit.range.start.character, doc)
                val to = positionToOffset(textEdit.range.end.line, textEdit.range.end.character, doc)

                js.Dynamic.literal(
                  "from" -> from,
                  "to" -> to,
                  "insert" -> textEdit.newText
                )
              }

              view.dispatch(js.Dynamic.literal(
                "changes" -> js.Array(changes*)
              ).asInstanceOf[TransactionSpec])
            }

            Callback.empty
          },
          name = scalaAction.title
        ).setMarkClass("cm-actions")
      }
    }
  }

  private def getDecorations(props: CodeEditor, doc: Text): js.Array[Diagnostic] = {
    val errors = props.compilationInfos
      .filter(prob => prob.line.isDefined)
      .flatMap(problemToDiagnostics(_, doc))

    val runtimeErrors = props.runtimeError.map(runtimeError => {
      val line = runtimeError.line.getOrElse(1).min(doc.lines.toInt)
      val lineInfo = doc.line(line)
      val msg = if (runtimeError.fullStack.nonEmpty) runtimeError.fullStack else runtimeError.message

      Diagnostic(lineInfo.from, msg, codemirrorLintStrings.error, lineInfo.to)
          .setRenderMessage((_: EditorView) => {
            val wrapper = dom.document.createElement("pre")
            wrapper.innerHTML = HTMLFormatter.format(msg)
            wrapper
          })
    })

    (errors ++ runtimeErrors).toJSArray
  }

  private def getExtension(editorMode: EditorMode): Extension = {
    editorMode match {
      case Default => js.Array[Any]()
      case Vim     => vim()
      case Emacs   => emacs()
    }
  }

  private def updateDiagnostics(editorView: UseStateF[CallbackTo, EditorView], prevProps: Option[CodeEditor], props: CodeEditor): Callback = {
    Callback {
      editorView.value.dispatch(setDiagnostics(editorView.value.state, getDecorations(props, editorView.value.state.doc)))
    }.when_(
      prevProps.isDefined &&
        props.value == editorView.value.state.doc.toString() && (
        prevProps.get.compilationInfos != props.compilationInfos ||
          prevProps.get.runtimeError != props.runtimeError
      )
    )
  }

  private def updateComponent(
      props: CodeEditor,
      ref: Ref.Simple[Element],
      prevProps: Option[CodeEditor],
      editorView: UseStateF[CallbackTo, EditorView]
  ): Callback = {
    Editor.updateCode(editorView, props) >>
      Editor.updateTheme(ref, prevProps, props, editorView) >>
      updateDiagnostics(editorView, prevProps, props) >>
      DecorationProvider.updateDecorations(editorView, prevProps, props) >>
      InteractiveProvider.didDirectivesChange(prevProps, props) >>
      InteractiveProvider.reloadMetalsConfiguration(editorView, prevProps, props, () => sharedHighlighter)
  }

  val hooksComponent =
    ScalaFnComponent
      .withHooks[CodeEditor]
      .useRef(Ref[Element])
      .useRef[Option[CodeEditor]](None)
      .useState(new EditorView())
      .useEffectOnMountBy((props, ref, prevProps, editorView) =>
        init(props, ref.value, editorView)
      )
      .useEffectBy(
        (props, ref, prevProps, editorView) =>
          Callback {
            if (prevProps.value.exists(_.editorMode != props.editorMode)) {
              val modeExtension: Extension =
                getExtension(props.editorMode)
              editorView.value.dispatch(
                TransactionSpec().setEffects(
                  Editor.editorModeCompartment.reconfigure(modeExtension)
                )
              )
            }
          } >>
          updateComponent(props, ref.value, prevProps.value, editorView) >>
          prevProps.set(Some(props))
      )
      .render((_, ref, _, _) => Editor.render(ref.value))

}
