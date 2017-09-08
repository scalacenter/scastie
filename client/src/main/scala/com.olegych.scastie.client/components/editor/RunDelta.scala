package com.olegych.scastie.client.components.editor

import com.olegych.scastie.client.components._

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.extra.Reusability

import codemirror.TextAreaEditor

private[editor] object RunDelta {

  val editorShouldRefresh: Reusability[Editor] = {
    Reusability.byRef ||
    (
      Reusability.by((_: Editor).attachedDoms) &&
      Reusability.by((_: Editor).instrumentations) &&
      Reusability.by((_: Editor).compilationInfos) &&
      Reusability.by((_: Editor).runtimeError) &&
      Reusability.by((_: Editor).completions)
    )
  }

  def apply(editor: TextAreaEditor,
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

        editor.setOption("theme", s"solarized $theme")
      }.when_(themeChanged)
    }

    def setCode: Callback = {
      val codeChanged =
        currentProps.map(_.code) != Some(nextProps.code)

      Callback {
        val doc = editor.getDoc()
        if (doc.getValue() != nextProps.code) {
          val prevScrollPosition = editor.getScrollInfo()
          doc.setValue(nextProps.code)
          editor.scrollTo(prevScrollPosition.left, prevScrollPosition.top)
        }
      }.when_(codeChanged)
    }

    def setLineNumbers: Callback = {
      val lineNumbersChanged =
        currentProps.map(_.showLineNumbers) != Some(nextProps.showLineNumbers)

      Callback {
        editor.setOption("lineNumbers", nextProps.showLineNumbers)
      }.when_(lineNumbersChanged)
    }

    def setTypeAtInfo: Callback = {
      val typeAtInfoChanged =
        currentProps.map(_.typeAtInfo) != Some(nextProps.typeAtInfo)

      if (typeAtInfoChanged) {
        val updateMessage =
          Callback {
            state.hoverMessage.updateMessage(nextProps.typeAtInfo.get.typeInfo)
          }.when_(nextProps.typeAtInfo.isDefined)

        updateMessage >>
          modState(_.copy(typeAt = nextProps.typeAtInfo))
      } else {
        Callback(())
      }
    }

    def refresh: Callback = {
      val shouldRefresh =
        currentProps
          .map(c => !editorShouldRefresh.test(c, nextProps))
          .getOrElse(true)

      Callback(
        scalajs.js.timers.setTimeout(10)(
          editor.refresh()
        )
      ).when_(shouldRefresh)
    }

    setTheme >>
      setCode >>
      setLineNumbers >>
      setTypeAtInfo >>
      ProblemAnnotations(editor, currentProps, nextProps, state, modState) >>
      RenderAnnotations(editor, currentProps, nextProps, state, modState) >>
      RuntimeErrorAnnotations(editor, currentProps, nextProps, state, modState) >>
      AutocompletionRender(editor, currentProps, nextProps, state, modState) >>
      refresh
  }
}
