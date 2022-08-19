package com.olegych.scastie.client.components.editor

import org.scalajs.dom
import typings.codemirrorState.anon
import typings.codemirrorView.mod.EditorView
import typings.codemirrorView.mod.{KeyBinding => JSKeyBinding}

import scalajs.js

object EditorKeymaps {
  import typings.codemirrorCommands.mod._

  private def presentationMode(editor: Editor): Unit = {
    if (!editor.isEmbedded) {
      editor.togglePresentationMode.runNow()
      if (!editor.isPresentationMode) {
        dom.window.alert("Press F8 again to leave the presentation mode")
      }
    }
  }

  def keymapping(e: Editor) =
    typings.codemirrorView.mod.keymap.of(
      js.Array(
        KeyBinding.fromCommand(insertTab, new Key("Tab")),
        KeyBinding(_ => e.saveOrUpdate.runNow(), new Key("Ctrl-s", "Meta-s"), true),
        KeyBinding(_ => e.saveOrUpdate.runNow(), new Key("Ctrl-Enter", "Meta-Enter"), true),
        KeyBinding(_ => e.openNewSnippetModal.runNow(), new Key("Ctrl-m", "Meta-m"), true),
        KeyBinding(_ => e.clear.runNow(), new Key("F1"), true),
        KeyBinding(_ => e.toggleHelp.runNow(), new Key("F3"), true),
        KeyBinding(_ => e.toggleConsole.runNow(), new Key("F6"), true),
        KeyBinding(_ => e.formatCode.runNow(), new Key("F7"), true),
        KeyBinding(_ => presentationMode(e), new Key("F8"), true),
      )
    )

}

case class Key(default: String, linux: String, mac: String, win: String) {
  def this(default: String) = {
    this(default, default, default, default)
  }

  def this(default: String, mac: String) = {
    this(default, default, mac, default)
  }
}

object KeyBinding {
  def fromCommand(action: anon.Dispatch => Boolean, key: Key, preventDefault: Boolean = false): JSKeyBinding = {
    JSKeyBinding(x => action(x.asInstanceOf[anon.Dispatch]))
      .setKey(key.default)
      .setLinux(key.linux)
      .setMac(key.mac)
      .setWin(key.win)
      .setPreventDefault(preventDefault)
  }

  def apply(action: EditorView => Unit, key: Key, preventDefault: Boolean = false): JSKeyBinding = {
    JSKeyBinding(editorView => { action(editorView); true })
      .setKey(key.default)
      .setLinux(key.linux)
      .setMac(key.mac)
      .setWin(key.win)
      .setPreventDefault(preventDefault)
  }
}
