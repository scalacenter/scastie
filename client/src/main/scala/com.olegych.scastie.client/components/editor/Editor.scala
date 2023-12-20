package com.olegych.scastie.client.components.editor

import japgolly.scalajs.react._
import org.scalablytyped.runtime.StringDictionary
import org.scalajs.dom.Element
import typings.codemirrorState.mod._
import typings.codemirrorView.anon
import typings.codemirrorView.mod._
import typings.replitCodemirrorIndentationMarkers.anon.ActiveDark
import typings.replitCodemirrorIndentationMarkers.mod._

import scalajs.js
import vdom.all._

trait Editor {
  val isDarkTheme: Boolean
  val value: String

  lazy val codemirrorTheme = EditorView.theme(StringDictionary(), anon.Dark().setDark(isDarkTheme))
}

object Editor {
  val editorTheme = new Compartment()

  private val indentationMarkersColors = ActiveDark()
    .setDark("#2a4c55")
    .setActiveDark("#3f5e66")

  val indentationMarkersExtension = indentationMarkers(
    IndentationMarkerConfiguration()
      .setColors(indentationMarkersColors)
      .setThickness(2.0)
  )

  def render(ref: Ref.Simple[Element]): VdomElement =
    div(cls := "editor-wrapper cm-s-solarized cm-s-light").withRef(ref)

  def updateTheme(ref: Ref.Simple[Element], prevProps: Option[Editor], props: Editor, editorView: hooks.Hooks.UseStateF[CallbackTo, EditorView]): Callback =
    ref
      .foreach(ref => {
        val cssTheme = if (props.isDarkTheme) "dark" else "light"
        editorView.value.dispatch(TransactionSpec().setEffects(editorTheme.reconfigure(props.codemirrorTheme)))
        ref.setAttribute("class", s"editor-wrapper cm-s-solarized cm-s-$cssTheme")
      })
      .when_(prevProps.map(_.isDarkTheme != props.isDarkTheme).getOrElse(true))

  def updateCode(editorView: Hooks.UseStateF[CallbackTo, EditorView], newState: Editor): Callback = {
    Callback {
      editorView.value.dispatch(TransactionSpec().setChanges(new js.Object {
        var from = 0
        var to = editorView.value.state.doc.length
        var insert = newState.value
      }.asInstanceOf[ChangeSpec]))
    }.when_(editorView.value.state.doc.toString() != newState.value)
  }

}
