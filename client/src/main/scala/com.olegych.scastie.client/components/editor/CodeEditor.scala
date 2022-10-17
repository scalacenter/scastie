package com.olegych.scastie.client.components.editor

import com.olegych.scastie.api
import com.olegych.scastie.client.HTMLFormatter
import japgolly.scalajs.react._
import org.scalajs.dom
import org.scalajs.dom.{Element, HTMLElement}
import typings.codemirrorLanguage.mod
import typings.codemirrorLint.codemirrorLintStrings
import typings.codemirrorLint.mod._
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._

import scalajs.js
import vdom.all._
import JsUtils._
import hooks.Hooks.UseStateF

final case class CodeEditor(visible: Boolean,
                            isDarkTheme: Boolean,
                            isPresentationMode: Boolean,
                            isWorksheetMode: Boolean,
                            isEmbedded: Boolean,
                            showLineNumbers: Boolean,
                            value: String,
                            attachedDoms: Map[String, HTMLElement],
                            instrumentations: Set[api.Instrumentation],
                            compilationInfos: Set[api.Problem],
                            runtimeError: Option[api.RuntimeError],
                            saveOrUpdate: Reusable[Callback],
                            clear: Reusable[Callback],
                            openNewSnippetModal: Reusable[Callback],
                            toggleHelp: Reusable[Callback],
                            toggleConsole: Reusable[Callback],
                            toggleLineNumbers: Reusable[Callback],
                            togglePresentationMode: Reusable[Callback],
                            formatCode: Reusable[Callback],
                            codeChange: String ~=> Callback,
) extends Editor {
  @inline def render: VdomElement = CodeEditor.hooksComponent(this)
}

object CodeEditor {

  private def init(props: CodeEditor, ref: Ref.Simple[Element], editorView: UseStateF[CallbackTo, EditorView]): Callback =
    ref.foreachCB(divRef => {
      val editor = new EditorView(new EditorViewConfig {
        state = EditorState.create(new EditorStateConfig {
          doc = props.value
          extensions = js.Array[Any](
            StateField
              .define(StateFieldSpec[Set[api.Instrumentation]](_ => props.instrumentations, (value, _) => value))
              .extension,
            typings.codemirror.mod.basicSetup,
            DecorationProvider(props),
            EditorState.tabSize.of(2),
            typings.codemirrorState.mod.Prec.highest(EditorKeymaps.keymapping(props)),
            mod.StreamLanguage.define(typings.codemirrorLegacyModes.clikeMod.scala_),
            SyntaxHighlightingTheme.highlightingTheme,
            lintGutter(),
            mod.codeFolding(),
            OnChangeHandler(props.codeChange),
          )
        })
        parent = divRef
      })

      editorView.setState(editor)
    })

  private def getDecorations(props: CodeEditor, doc: Text): js.Array[Diagnostic] = {
    val errors = props.compilationInfos
      .filter(prob => prob.line.isDefined && prob.line.get <= doc.lines)
      .map(problem => {
        val line = problem.line.get
        val lineInfo = doc.line(line)

        Diagnostic(lineInfo.from, HTMLFormatter.format(problem.message), parseSeverity(problem.severity), lineInfo.to)
          .setRenderMessage(CallbackTo {
            val wrapper = dom.document.createElement("pre")
            wrapper.innerHTML = HTMLFormatter.format(problem.message)
            wrapper
          })

      })
    val runtimeErrors = props.runtimeError.map(runtimeError => {
      val line = runtimeError.line.getOrElse(1).min(doc.lines.toInt)
      val lineInfo = doc.line(line)
      val msg = if (runtimeError.fullStack.nonEmpty) runtimeError.fullStack else runtimeError.message
      Diagnostic(lineInfo.from, HTMLFormatter.format(msg), codemirrorLintStrings.error, lineInfo.to)
    })

    js.Array((errors ++ runtimeErrors).toSeq: _*)
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
      Editor.updateTheme(ref, prevProps, props) >>
      updateDiagnostics(editorView, prevProps, props) >>
      DecorationProvider.updateDecorations(editorView, prevProps, props)
  }

  val hooksComponent =
    ScalaFnComponent
      .withHooks[CodeEditor]
      .useRef(Ref[Element])
      .useRef[Option[CodeEditor]](None)
      .useState(new EditorView())
      .useLayoutEffectOnMountBy((props, ref, prevProps, editorView) => init(props, ref.value, editorView))
      .useEffectBy(
        (props, ref, prevProps, editorView) => updateComponent(props, ref.value, prevProps.value, editorView) >> prevProps.set(Some(props))
      )
      .render((_, ref, _, _) => Editor.render(ref.value))

}
