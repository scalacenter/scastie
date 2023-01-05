package com.olegych.scastie.client.components.editor

import japgolly.scalajs.react._
import org.scalajs.dom.Element
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._

import scalajs.js
import vdom.all._

trait Editor {
  val isDarkTheme: Boolean
  val value: String
}

object Editor {
  def render(ref: Ref.Simple[Element]): VdomElement =
    div(cls := "editor-wrapper cm-s-solarized cm-s-light").withRef(ref)

  def updateTheme(ref: Ref.Simple[Element], prevProps: Option[Editor], props: Editor): Callback =
    ref
      .foreach(ref => {
        val theme = if (props.isDarkTheme) "dark" else "light"
        ref.setAttribute("class", s"editor-wrapper cm-s-solarized cm-s-$theme")
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
