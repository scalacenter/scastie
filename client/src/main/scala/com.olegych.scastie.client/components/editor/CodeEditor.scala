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
import typings.codemirrorAutocomplete.mod._
import typings.codemirrorCommands.mod._
import typings.codemirrorLanguage.mod._
import typings.codemirrorSearch.mod._

import scalajs.js
import vdom.all._
import JsUtils._
import hooks.Hooks.UseStateF
import netscape.javascript.JSObject
import typings.codemirrorAutocomplete.anon
import play.api.libs.json.Json
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalajs.js.Thenable.Implicits._
import scala.concurrent.Await
import js.JSConverters._
import typings.codemirrorState
import typings.std.Node
import typings.codemirrorView

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
                            target: api.ScalaTarget,
                            dependencies: Set[api.ScalaDependency])
    extends Editor {
  @inline def render: VdomElement = CodeEditor.hooksComponent(this)
}

case class InteractiveProvider(scalaTarget: api.ScalaTarget, dependencies: Set[api.ScalaDependency]) {
  def extension = autocompletion(autocompletionConfig)

  def toLSPRequest(code: String, offset: Int): api.LSPRequestDTO = {
    val metalsOption = api.ScastieMetalsOptions(dependencies, scalaTarget)
    val offsetParams = api.ScastieOffsetParams(code, offset)
    api.LSPRequestDTO(metalsOption, offsetParams)
  }

  def makeRequest(req: api.LSPRequestDTO, endpoint: String) =
    dom.fetch(s"http://localhost:8000/metals/$endpoint", js.Dynamic.literal(
      body = Json.toJson(req).toString,
      method = dom.HttpMethod.POST
    ).asInstanceOf[dom.RequestInit])

  def makeRequestInfo(req: api.CompletionInfoRequest, endpoint: String) =
    dom.fetch(s"http://localhost:8000/metals/completionInfo", js.Dynamic.literal(
      body = Json.toJson(req).toString,
      method = dom.HttpMethod.POST
    ).asInstanceOf[dom.RequestInit])

  val completionsF: js.Function1[CompletionContext, js.Promise[CompletionResult]] = { ctx =>
    val word: anon.Text = ctx.matchBefore(js.RegExp("\\.?\\w*")).asInstanceOf[anon.Text]

    val request = toLSPRequest(ctx.state.doc.toString(), ctx.pos.toInt)
    val from = if (word.text.headOption == Some('.')) word.from + 1 else word.from

    if (word == null || (word.from == word.to && !ctx.explicit)) {
      null
    } else {
      (for {
        res <- makeRequest(request, "complete")
        text <- res.text()
      } yield {
        val completions = Json.parse(text).asOpt[Set[api.CompletionItemDTO]].getOrElse(Set.empty).map {
          case cmp @ api.CompletionItemDTO(label, info, tpe, boost, insertInstructions, details, symbol) => {
            Completion(label)
              .setInfo({
                val infoFunction: js.Function1[Completion, js.Promise[dom.Node]] = _ =>
                    (for {
                      res <- makeRequestInfo(api.CompletionInfoRequest(request.options, cmp), "")
                      text <- res.text()
                    } yield {
                      if (text.nonEmpty) {
                        val node = dom.document.createElement("pre")
                        node.textContent = text
                        node
                      } else {
                        null
                      }
                    }).toJSPromise
               infoFunction

              })
              .setType(tpe)
              .setBoost(boost.getOrElse(0).toDouble)
              .setApplyFunction4((view, cmp, from, to) => Callback {
                view.dispatch(
                  TransactionSpec().setChanges(
                    js.Dynamic.literal(
                      from = from.toDouble,
                      to = to.toDouble,
                      insert = insertInstructions.text
                    ).asInstanceOf[ChangeSpec]
                  ).setSelection(
                    codemirrorState.anon.Anchor(from.toDouble + insertInstructions.cursorMove)
                  )
              )})
          }
        }
        CompletionResult(from, completions.toJSArray).setValidFor(js.RegExp("^\\w*$"))
      }).toJSPromise
    }
  }

  private val autocompletionConfig = CompletionConfig()
    .setOverrideVarargs(completionsF)
    .setIcons(true)
    .setDefaultKeymap(true)

}

object Interactive {
  val interactiveExtension = new Compartment()

  // def extension = interactiveExtension.of()
}

object CodeEditor {

  val interactive = new Compartment()

  private def init(props: CodeEditor, ref: Ref.Simple[Element], editorView: UseStateF[CallbackTo, EditorView]): Callback =
    ref.foreachCB(divRef => {
      val editor = new EditorView(new EditorViewConfig {
        state = EditorState.create(new EditorStateConfig {
          doc = props.value
          extensions = js.Array[Any](
            lineNumbers(),
            highlightActiveLineGutter(),
            highlightSpecialChars(),
            history(),
            foldGutter(),
            drawSelection(),
            dropCursor(),
            EditorState.allowMultipleSelections.of(true),
            indentOnInput(),
            bracketMatching(),
            closeBrackets(),
            rectangularSelection(),
            crosshairCursor(),
            highlightActiveLine(),
            highlightSelectionMatches(),
            keymap.of(closeBracketsKeymap ++ defaultKeymap ++ historyKeymap ++ foldKeymap ++ completionKeymap ++ lintKeymap ++ searchKeymap),
            StateField
              .define(StateFieldSpec[Set[api.Instrumentation]](_ => props.instrumentations, (value, _) => value))
              .extension,
            DecorationProvider(props),
            EditorState.tabSize.of(2),
            Prec.highest(EditorKeymaps.keymapping(props)),
            interactive.of(InteractiveProvider(props.target, props.dependencies).extension),
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

    (errors ++ runtimeErrors).toJSArray
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

  private def reloadMetalsConfiguration(
    editorView: UseStateF[CallbackTo, EditorView],
    prevProps: Option[CodeEditor],
    props: CodeEditor
  ): Callback = {
    Callback {
      val newBuildConfiguration = InteractiveProvider(props.target, props.dependencies)
      val effects = interactive.reconfigure(newBuildConfiguration.extension)
      editorView.value.dispatch(TransactionSpec().setEffects(effects.asInstanceOf[StateEffect[Any]]))
    }.when_(prevProps.isDefined && (
      props.target != prevProps.get.target || props.dependencies != prevProps.get.dependencies
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
      DecorationProvider.updateDecorations(editorView, prevProps, props) >>
      reloadMetalsConfiguration(editorView, prevProps, props)
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
