package org.scastie.client.components.editor

import org.scastie.api
import org.scastie.client._
import japgolly.scalajs.react._
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._
import typings.highlightJs.mod.{HighlightOptions => HLJSOptions}
import typings.markedHighlight.mod._
import typings.marked.mod.marked.MarkedExtension

import scala.util.Try

import scalajs.js
import hooks.Hooks.UseStateF
import org.scastie.client.scalacli.ScalaCliUtils

case class InteractiveProvider(
  dependencies: Set[api.ScalaDependency],
  target: api.ScalaTarget,
  code: String,
  metalsStatus: MetalsStatus,
  updateStatus: MetalsStatus ~=> Callback,
  isWorksheetMode: Boolean,
  isEmbedded: Boolean,
  syntaxHighlighterGetter: () => Option[SyntaxHighlighter],
) extends MetalsClient with MetalsAutocompletion with MetalsHover with MetalsSignatureHelp with MetalsDiagnostics {

  def syntaxHighlighter: Option[SyntaxHighlighter] = syntaxHighlighterGetter()

  def extension: js.Array[Any] = js.Array[Any](metalsHover, metalsAutocomplete, metalsSignatureHelp, metalsDiags)

}

object InteractiveProvider {

  def apply(props: CodeEditor, syntaxHighlighterGetter: () => Option[SyntaxHighlighter]): InteractiveProvider = {
    InteractiveProvider(
      props.dependencies,
      props.target,
      props.value,
      props.metalsStatus,
      props.setMetalsStatus,
      props.isWorksheetMode,
      props.isEmbedded,
      syntaxHighlighterGetter
    )
  }

  val interactive = new Compartment()

  def renderMarkdown(markdown: String): String = {
    typings.marked.mod.marked.`package`.parse(markdown).asInstanceOf[String]
  }

  val highlightJS = typings.highlightJs.mod.default
  val highlightF: (String, String, String) => String = (str, lang, _) => {
    if (lang != null && highlightJS.getLanguage(lang) != null && lang != "") {
      Try { highlightJS.highlight(str, HLJSOptions(lang)).value}.getOrElse(str)
    } else {
      str
    }
  }

  val marked = typings.marked.mod.marked.`package`
  marked.use(markedHighlight(SynchronousOptions.apply(highlightF)).asInstanceOf[MarkedExtension])
  marked.setOptions(typings.marked.mod.marked.MarkedOptions()
    .setHeaderIds(false)
    .setMangle(false)
  )

  private def wasMetalsToggled(prevProps: CodeEditor, props: CodeEditor): Boolean =
    (prevProps.metalsStatus == MetalsDisabled && props.metalsStatus == MetalsLoading) ||
    (prevProps.metalsStatus != MetalsDisabled && props.metalsStatus == MetalsDisabled)

  private def requiresDirectiveReload(prevProps: CodeEditor, props: CodeEditor): Boolean =
    (prevProps.metalsStatus != OutdatedScalaCli && props.metalsStatus == OutdatedScalaCli)

  private def takeDirectives(code: String) =
    code.split("\n").takeWhile(_.startsWith("//>")).toList

  import scala.concurrent.duration._
  import scala.scalajs.js.timers._
  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  val didDirectivesChange: (Option[CodeEditor], CodeEditor) => Callback = {
    var timeout: js.UndefOr[js.timers.SetTimeoutHandle] = js.undefined
    var previousDirectives: List[String] = List.empty

    (prev, current) => Callback {
      timeout.foreach(clearTimeout)
      timeout = setTimeout(1000.millis) {
        val newDirectives = takeDirectives(current.value)
        if (previousDirectives != newDirectives)
          previousDirectives = newDirectives
          current.setMetalsStatus(OutdatedScalaCli).runNow()
        if (previousDirectives != newDirectives) current.setMetalsStatus(OutdatedScalaCli).runNow()
      }
    }.when_(
      (prev.map(_.value).getOrElse("") != current.value) &&
      (current.target.targetType == api.ScalaTargetType.ScalaCli) &&
      (current.metalsStatus != MetalsDisabled)
    )
  }

  private def didConfigChange(prevProps: CodeEditor, props: CodeEditor): Boolean =
      props.target != prevProps.target ||
        props.dependencies != prevProps.dependencies ||
        props.isWorksheetMode != prevProps.isWorksheetMode

  def reloadMetalsConfiguration(
    editorView: UseStateF[CallbackTo, EditorView],
    prevProps: Option[CodeEditor],
    props: CodeEditor,
    syntaxHighlighterGetter: () => Option[SyntaxHighlighter]
  ): Callback = {
      val newExtension: AsyncCallback[InteractiveProvider] =
        if (props.metalsStatus != MetalsDisabled && props.target.targetType == api.ScalaTargetType.ScalaCli)
          AsyncCallback.fromFuture {
            ScalaCliUtils.parse(takeDirectives(props.value)).map { case (scalaTarget, dependencies) =>
              InteractiveProvider(
                dependencies,
                scalaTarget,
                props.value,
                props.metalsStatus,
                props.setMetalsStatus,
                props.isWorksheetMode,
                props.isEmbedded,
                syntaxHighlighterGetter
              )
            }
          }
        else AsyncCallback.delay {
          InteractiveProvider(props,syntaxHighlighterGetter)
        }

      newExtension.map(extension =>
          editorView
            .value
            .dispatch(TransactionSpec()
            .setEffects(interactive.reconfigure(extension.extension))))
            .toCallback

    }.when_(props.visible && prevProps.exists(prevProps => {
      didConfigChange(prevProps, props) ||
      (prevProps.visible != props.visible) ||
      wasMetalsToggled(prevProps, props) ||
      requiresDirectiveReload(prevProps, props)
    }))
}

