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

case class InteractiveProvider(
  dependencies: Set[api.ScalaDependency],
  target: api.ScalaTarget,
  code: String,
  metalsStatus: MetalsStatus,
  updateStatus: MetalsStatus ~=> Callback,
  updateSettings: api.ScastieMetalsOptions ~=> Callback,
  isWorksheetMode: Boolean,
  isEmbedded: Boolean,
) extends MetalsClient with MetalsAutocompletion with MetalsHover {

  def extension: js.Array[Any] = js.Array[Any](metalsHover, metalsAutocomplete)

}

object InteractiveProvider {

  def apply(props: CodeEditor): InteractiveProvider = {
    InteractiveProvider(
      props.dependencies,
      props.target,
      props.value,
      props.metalsStatus,
      props.setMetalsStatus,
      props.updateSettings,
      props.isWorksheetMode,
      props.isEmbedded
    )
  }

  val interactive = new Compartment()

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

  val didDirectivesChange: (Option[CodeEditor], CodeEditor) => Unit = {
    var timeout: js.UndefOr[js.timers.SetTimeoutHandle] = js.undefined
    var originalPrevious: Option[CodeEditor] = None

    (prev, current) =>
      if (originalPrevious.isEmpty && prev.isDefined) originalPrevious = prev
      timeout.foreach(clearTimeout)
      timeout = setTimeout(5000.millis) {
        originalPrevious.map { prev => {
          val previousDirectives = takeDirectives(prev.value)
          val newDirectives = takeDirectives(current.value)
          originalPrevious = Some(current)
          if (previousDirectives != newDirectives) current.setMetalsStatus(OutdatedScalaCli).runNow()
        }}
      }
  }

  private def didConfigChange(prevProps: CodeEditor, props: CodeEditor): Boolean =
      props.target != prevProps.target ||
        props.dependencies != prevProps.dependencies ||
        props.isWorksheetMode != prevProps.isWorksheetMode

  def reloadMetalsConfiguration(
    editorView: UseStateF[CallbackTo, EditorView],
    prevProps: Option[CodeEditor],
    props: CodeEditor
  ): Callback = {
    if (props.metalsStatus != MetalsDisabled && props.target.targetType == api.ScalaTargetType.ScalaCli)
      didDirectivesChange(prevProps, props)
    Callback {
      val extension = InteractiveProvider(
        props.dependencies,
        props.target,
        props.value,
        props.metalsStatus,
        props.setMetalsStatus,
        props.updateSettings,
        props.isWorksheetMode,
        props.isEmbedded
      ).extension

      val effects = interactive.reconfigure(extension)
      editorView.value.dispatch(TransactionSpec().setEffects(effects))
    }.when_(props.visible && prevProps.exists(prevProps => {
      didConfigChange(prevProps, props) ||
      (prevProps.visible != props.visible) ||
      wasMetalsToggled(prevProps, props) ||
      requiresDirectiveReload(prevProps, props)
    }))
  }

}
