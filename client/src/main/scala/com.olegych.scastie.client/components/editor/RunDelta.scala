package com.olegych.scastie.client.components.editor

import com.olegych.scastie.client.components._

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.Reusability
import typings.codemirrorState.mod.EditorState


private[editor] object RunDelta {

  val editorShouldRefresh: Reusability[Editor] =  Reusability.byRef

  def apply(editor: Editor,
            currentProps: Option[Editor],
            nextProps: Editor,
            state: EditorState,
            modState: (EditorState => EditorState) => Callback): Callback = {
    def setTheme: Callback = {
      val themeChanged =
        currentProps.map(_.isDarkTheme) != Some(nextProps.isDarkTheme)

      Callback {
        val theme =
          if (nextProps.isDarkTheme) "dark"
          else "light"

        // editor.setOption("theme", s"solarized $theme")
      }.when_(themeChanged)
    }

    def setCode: Callback = {
      val codeChanged =
        currentProps.map(_.code) != Some(nextProps.code)

      Callback {
        // state.doc = nextProps.code
        // val doc = editor.getDoc()
        // if (doc.getValue() != nextProps.code) {
        //   val prevScrollPosition = editor.getScrollInfo()
        //   doc.setValue(nextProps.code)
        //   editor.scrollTo(prevScrollPosition.left, prevScrollPosition.top)
        // }
      }.when_(codeChanged)
    }

    def setLineNumbers: Callback = {
      val lineNumbersChanged =
        currentProps.map(_.showLineNumbers) != Some(nextProps.showLineNumbers)

      Callback {
        // editor.setOption("lineNumbers", nextProps.showLineNumbers)
      }.when_(lineNumbersChanged)
    }

    setTheme >>
      setCode >>
      setLineNumbers
  }
}
