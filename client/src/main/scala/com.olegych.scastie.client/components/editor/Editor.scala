package com.olegych.scastie.client.components.editor

import com.olegych.scastie.api

import com.olegych.scastie.client.AttachedDoms

import japgolly.scalajs.react._, vdom.all._, extra._

final case class Editor(visible: Boolean,
                        isDarkTheme: Boolean,
                        isPresentationMode: Boolean,
                        isEmbedded: Boolean,
                        showLineNumbers: Boolean,
                        code: String,
                        attachedDoms: AttachedDoms,
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
                        codeChange: String ~=> Callback) {

  @inline def render: VdomElement = Editor.component(this)
}

object Editor {
  import EditorReusability._

  codemirror.AutoRefresh
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
      .configure(Reusability.shouldComponentUpdate)
      .componentDidMount(_.backend.start())
      .componentDidUpdate(u => Callback.traverseOption(u.currentState.editor)(e => Callback(e.focus())))
      .componentWillUnmount(_.backend.stop())
      .componentWillReceiveProps { scope =>
        scope.state.editor
          .map(
            editor =>
              RunDelta(
                editor = editor,
                currentProps = Some(scope.currentProps),
                nextProps = scope.nextProps,
                state = scope.state,
                modState = (f => scope.modState(f))
            )
          )
          .getOrElse(Callback.empty)

      }
      .build
}
