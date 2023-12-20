package com.olegych.scastie.client.components.editor

import com.olegych.scastie.api
import com.olegych.scastie.client._
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
  metalsStatus: MetalsStatus,
  updateStatus: MetalsStatus ~=> Callback,
  isWorksheetMode: Boolean,
  isEmbedded: Boolean,
) extends MetalsClient with MetalsAutocompletion with MetalsHover {

  def extension: js.Array[Any] = js.Array[Any](
    metalsHover,
    metalsAutocomplete
  )
}

object InteractiveProvider {

  def apply(props: CodeEditor): InteractiveProvider = {
    InteractiveProvider(
      props.dependencies,
      props.target,
      props.metalsStatus,
      props.setMetalsStatus,
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

  private def didConfigChange(prevProps: CodeEditor, props: CodeEditor): Boolean =
      props.target != prevProps.target ||
        props.dependencies != prevProps.dependencies ||
        props.isWorksheetMode != prevProps.isWorksheetMode

  def reloadMetalsConfiguration(
    editorView: UseStateF[CallbackTo, EditorView],
    prevProps: Option[CodeEditor],
    props: CodeEditor
  ): Callback = {
    Callback {
      val extension = InteractiveProvider(
        props.dependencies,
        props.target,
        props.metalsStatus,
        props.setMetalsStatus,
        props.isWorksheetMode,
        props.isEmbedded
      ).extension

      val effects = interactive.reconfigure(extension)
      editorView.value.dispatch(TransactionSpec().setEffects(effects))
    }.when_(props.visible && prevProps.exists(prevProps => {
      didConfigChange(prevProps, props) || (prevProps.visible != props.visible) || wasMetalsToggled(prevProps, props)
    }))
  }

}

