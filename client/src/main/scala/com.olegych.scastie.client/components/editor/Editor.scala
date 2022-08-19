package com.olegych.scastie.client.components.editor

import com.olegych.scastie.api
import com.olegych.scastie.client.HTMLFormatter
import japgolly.scalajs.react._
import org.scalajs.dom
import org.scalajs.dom.Element
import typings.codemirrorLanguage.mod
import typings.codemirrorLint.codemirrorLintStrings
import typings.codemirrorLint.mod._
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._

import scalajs.js
import vdom.all._
import JsUtils._
import hooks.Hooks.UseStateF

final case class Editor(visible: Boolean,
                        isDarkTheme: Boolean,
                        isPresentationMode: Boolean,
                        isWorksheetMode: Boolean,
                        isEmbedded: Boolean,
                        showLineNumbers: Boolean,
                        code: String,
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
                        invalidateDecorations: api.Position ~=> Callback
) {
  Editor
  @inline def render: VdomElement = Editor.hooksComponent(this)
}

object Editor {

  private def render(ref: Ref.Simple[Element]): VdomElement =
    div(cls := "editor-wrapper cm-s-solarized cm-s-light").withRef(ref)

  private def init(props: Editor, ref: Ref.Simple[Element], editorView: UseStateF[CallbackTo, EditorView]): Callback =
    ref.foreachCB(divRef => {
      val editor = new EditorView(new EditorViewConfig {
        state = EditorState.create(new EditorStateConfig {
          doc = props.code
          extensions = js.Array[Any](
            typings.codemirror.mod.basicSetup,
            EditorState.tabSize.of(2),
            typings.codemirrorState.mod.Prec.highest(EditorKeymaps.keymapping(props)),
            OnChangeHandler(props.codeChange, props.invalidateDecorations),
            //TODO Type decoration are not ready yet.
            // StateField
            //   .define(StateFieldSpec[Set[api.Instrumentation]](_ => props.instrumentations, (value, _) => value))
            //   .extension,
            mod.StreamLanguage.define(typings.codemirrorLegacyModes.clikeMod.scala_),
            TypeDecorationProvider(props),
            SyntaxHighlightingTheme.highlightingTheme,
            lintGutter()
          )
        })
        parent = divRef
      })

      editorView.setState(editor)
    })

  private def updateCode(editorView: UseStateF[CallbackTo, EditorView], newState: Editor): Callback = {
    Callback {
      editorView.value.dispatch(TransactionSpec().setChanges(new js.Object {
        var from = 0
        var to = editorView.value.state.doc.length
        var insert = newState.code
      }.asInstanceOf[ChangeSpec]))
    }.when_(editorView.value.state.doc.toString() != newState.code)
  }

  private def getDecorations(props: Editor, doc: Text): js.Array[Diagnostic] = {
    val errors = props.compilationInfos
      .map(problem => {
        val line = problem.line.getOrElse(1)
        val lineInfo = doc.line(line)
        Diagnostic(lineInfo.from, HTMLFormatter.format(problem.message), parseSeverity(problem.severity), lineInfo.to)
          .setRenderMessage(CallbackTo {
            val wrapper  = dom.document.createElement("pre")
            wrapper.innerHTML = HTMLFormatter.format(problem.message)
            wrapper
          })

      })
    val runtimeErrors = props.runtimeError.map(runtimeError => {
        val line = runtimeError.line.getOrElse(1)
        val lineInfo = doc.line(line)
        Diagnostic(lineInfo.from, HTMLFormatter.format(runtimeError.message), codemirrorLintStrings.error, lineInfo.to)
      })

    js.Array((errors ++ runtimeErrors).toSeq:_*)
  }

  private def updateDiagnostics(editorView: UseStateF[CallbackTo, EditorView], prevProps: Option[Editor], props: Editor): Callback = {
    Callback {
      editorView.value.dispatch(setDiagnostics(editorView.value.state, getDecorations(props, editorView.value.state.doc)))
    }.when_(
      prevProps.isDefined &&
        props.code == editorView.value.state.doc.toString() && (
        prevProps.get.compilationInfos != props.compilationInfos ||
          prevProps.get.runtimeError != props.runtimeError
      )
    )
  }

  //TODO change logic behind theming
  private def updateTheme(ref: Ref.Simple[Element], prevProps: Option[Editor], props: Editor): Callback =
    ref.foreach(ref => {
      val theme = if (props.isDarkTheme) "dark" else "light"
      ref.setAttribute("class", s"editor-wrapper cm-s-solarized cm-s-$theme")
    }).when_(prevProps.isDefined && prevProps.get.isDarkTheme != props.isDarkTheme)


  val hooksComponent =
    ScalaFnComponent
      .withHooks[Editor]
      .useRef(Ref[Element])
      .useRef[Option[Editor]](None)
      .useState(new EditorView())
      .useLayoutEffectOnMountBy((props, ref, prevProps, editorView) => init(props, ref.value, editorView))
      // .useEffectBy((props, ref, prevProps, editorView) => TypeDecorationProvider.updateTypeDecorations(editorView, prevProps.value, props))
      .useEffectBy((props, ref, prevProps, _) => updateTheme(ref.value, prevProps.value, props))
      .useEffectBy((props, _, _, editorRef) => updateCode(editorRef, props))
      .useEffectBy((props, _, prevProps, editorRef) => updateDiagnostics(editorRef, prevProps.value, props))
      .useEffectBy((props, _, prevProps, _) => prevProps.set(Some(props)))
      .render((_, ref, _, _) => render(ref.value))

}
