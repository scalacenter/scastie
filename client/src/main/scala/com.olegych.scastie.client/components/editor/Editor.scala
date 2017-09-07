package com.olegych.scastie.client.components.editor

import com.olegych.scastie.api

import com.olegych.scastie.client.AttachedDoms

import japgolly.scalajs.react._, vdom.all._, extra._

import codemirror.TextAreaEditor

final case class Editor(isDarkTheme: Boolean,
                        isPresentationMode: Boolean,
                        isEmbedded: Boolean,
                        showLineNumbers: Boolean,
                        code: String,
                        attachedDoms: AttachedDoms,
                        instrumentations: Set[api.Instrumentation],
                        compilationInfos: Set[api.Problem],
                        runtimeError: Option[api.RuntimeError],
                        completions: List[api.Completion],
                        typeAtInfo: Option[api.TypeInfoAt],
                        run: Reusable[Callback],
                        saveOrUpdate: Reusable[Callback],
                        clear: Reusable[Callback],
                        openNewSnippetModal: Reusable[Callback],
                        toggleHelp: Reusable[Callback],
                        toggleConsole: Reusable[Callback],
                        toggleWorksheetMode: Reusable[Callback],
                        toggleTheme: Reusable[Callback],
                        toggleLineNumbers: Reusable[Callback],
                        togglePresentationMode: Reusable[Callback],
                        formatCode: Reusable[Callback],
                        codeChange: String ~=> Callback,
                        completeCodeAt: Int ~=> Callback,
                        requestTypeAt: (String, Int) ~=> Callback,
                        clearCompletions: Reusable[Callback]) {

  @inline def render: VdomElement = Editor.component(this)
}

object Editor {
  import EditorReusability._

  codemirror.Comment
  codemirror.Dialog
  codemirror.CloseBrackets
  codemirror.MatchBrackets
  codemirror.BraceFold
  codemirror.FoldCode
  codemirror.Search
  codemirror.SearchCursor
  codemirror.HardWrap
  codemirror.ShowHint
  codemirror.RunMode
  codemirror.SimpleScrollBars
  codemirror.MatchHighlighter
  codemirror.Sublime
  codemirror.CLike

  private val component =
    ScalaComponent
      .builder[Editor]("Editor")
      .initialState(EditorState())
      .renderBackend[EditorBackend]
      .componentWillReceiveProps{ scope =>
        scope.state.editor match {
          case Some(editor) => 
            RunDelta(
              editor = editor,
              currentProps = Some(scope.currentProps),
              nextProps = scope.nextProps,
              state = scope.state,
              modState = (f => scope.modState(f))
            )

          case None => 
            Callback(())
        }

      }
      .configure(Reusability.shouldComponentUpdate)
      .componentDidMount(_.backend.start())
      .componentWillUnmount(_.backend.stop())
      .build
}
