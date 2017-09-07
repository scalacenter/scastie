package com.olegych.scastie.client.components.editor

import org.scalajs.dom.raw.{HTMLElement, HTMLPreElement}

import com.olegych.scastie.api.Completion

import codemirror.{Hint, HintConfig, CodeMirror, TextAreaEditor, modeScala}

import codemirror.CodeMirror.{Pos => CMPosition}

import japgolly.scalajs.react.Callback

import org.scalajs.dom

import scala.scalajs.js

object AutocompletionRender {
  private def renderSingleAutocompletion(el: HTMLElement,
                                         completion: Completion,
                                         nextProps: Editor): Unit = {

    val hint = dom.document
      .createElement("span")
      .asInstanceOf[HTMLPreElement]

    hint.className = "name cm-def"
    hint.textContent = completion.hint

    val signature = dom.document
      .createElement("pre")
      .asInstanceOf[HTMLPreElement]

    signature.className = "signature"

    CodeMirror.runMode(completion.signature, modeScala, signature)

    val resultType = dom.document
      .createElement("pre")
      .asInstanceOf[HTMLPreElement]

    resultType.className = "result-type"

    CodeMirror.runMode(completion.resultType, modeScala, resultType)

    el.appendChild(hint)
    el.appendChild(signature)
    el.appendChild(resultType)

    if (nextProps.isPresentationMode) {
      val hintsDiv = signature.parentElement.parentElement

      hintsDiv.className = hintsDiv.className.concat(" presentation-mode")
    }
  }

  def apply(editor: TextAreaEditor,
            currentProps: Option[Editor],
            nextProps: Editor,
            state: EditorState,
            modState: (EditorState => EditorState) => Callback): Callback = {

    Callback(render(editor, currentProps, nextProps, state, modState))
  }

  def render(editor: TextAreaEditor,
             currentProps: Option[Editor],
             nextProps: Editor,
             state: EditorState,
             modState: (EditorState => EditorState) => Callback): Unit = {

    if (state.completionState == Requested ||
        state.completionState == NeedRender ||
        !nextProps.completions.equals(
          currentProps.getOrElse(nextProps).completions
        )) {

      state.loadingMessage.hide()

      val doc = editor.getDoc()
      val cursor = doc.getCursor()
      var fr = cursor.ch
      val to = cursor.ch
      val currLine = cursor.line
      val alphaNum = ('a' to 'z').toSet ++ ('A' to 'Z').toSet ++ ('0' to '9').toSet
      val lineContent = doc.getLine(currLine).getOrElse("")

      var i = fr - 1
      while (i >= 0 && alphaNum.contains(lineContent.charAt(i))) {
        fr = i
        i -= 1
      }

      val currPos = doc.indexFromPos(doc.getCursor())
      val filter = doc
        .getValue()
        .substring(doc.indexFromPos(new CMPosition {
          line = currLine; ch = fr
        }), currPos)

      // stop autocomplete if user reached brackets
      val enteredBrackets =
        doc.getValue().substring(currPos - 1, currPos + 1) == "()" &&
          state.completionState != Requested

      if (enteredBrackets || nextProps.completions.isEmpty) {
        modState(_.copy(completionState = Idle)).runNow()
      } else {
        // autopick single completion only if it's user's first request
        val completeSingle =
          nextProps.completions.length == 1 &&
            state.completionState == Requested

        CodeMirror.showHint(
          editor,
          (_, options) => {
            js.Dictionary(
              "from" -> new CMPosition {
                line = currLine; ch = fr
              },
              "to" -> new CMPosition {
                line = currLine; ch = to
              },
              "list" ->
                nextProps.completions
                  .filter(_.hint.startsWith(filter)) // FIXME: can place not 'important' completions first
                  .map { completion =>
                    HintConfig
                      .className("autocomplete")
                      .text(completion.hint)
                      .render(
                        (el, _, _) ⇒
                          renderSingleAutocompletion(el, completion, nextProps)
                      ): Hint
                  }
                  .to[js.Array]
            )
          },
          js.Dictionary[Any](
            "container" -> dom.document.querySelector(".CodeMirror"),
            "alignWithWord" -> true,
            "completeSingle" -> completeSingle
          )
        )

        modState(_.copy(completionState = Active)).runNow()

        if (completeSingle) {
          modState(_.copy(completionState = Idle)).runNow()
          nextProps.clearCompletions.runNow()
        }
      }
    } else {
      Callback(())
    }
  }
}
