package com.olegych.scastie.client.components.editor

import typings.codemirrorView.mod._


object EditorTextOps {
  val regex = """\.\w*|\w+""".r

  implicit class EditorTextOpsOps(view: EditorView) {
    def lineBeforeCursor: String = {
      val pos = view.state.selection.main.from
      val line = view.state.doc.lineAt(pos)
      val start = Math.max(line.from, pos - 250)
      line.text.slice(start.toInt - line.from.toInt, pos.toInt - line.from.toInt)
    }

    def matchesPreviousToken(previousToken: String): Boolean = {
      val matchedString = regex.findAllIn(lineBeforeCursor).iterator.toSeq
      matchedString.lastOption.exists(_.startsWith(previousToken))
    }

  }
}
