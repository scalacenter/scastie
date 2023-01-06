package com.olegych.scastie.client.components.editor

import org.scalajs.dom
import typings.codemirrorState.anon
import typings.codemirrorView.mod.EditorView
import typings.codemirrorView.mod.{KeyBinding => JSKeyBinding}
import com.olegych.scastie.client

import scalajs.js

object EditorKeymaps {
  import typings.codemirrorCommands.mod._

  private def presentationMode(editor: CodeEditor): Unit = {
    if (!editor.isEmbedded) {
      editor.togglePresentationMode.runNow()
      if (!editor.isPresentationMode) {
        dom.window.alert("Press F8 again to leave the presentation mode")
      }
    }
  }

  val saveOrUpdate = new Key("Ctrl-Enter", "Meta-Enter")
  val saveOrUpdateAlt = new Key("Ctrl-s", "Meta-s")
  val openNewSnippetModal = new Key("Ctrl-m", "Meta-m")
  val clear = new Key("Escape")
  val clearAlt = new Key("F1")
  val console = new Key("F3")
  val help = new Key("F5")
  val format = new Key("F6")
  val presentation = new Key("F8")

  def keymapping(e: CodeEditor) =
    typings.codemirrorView.mod.keymap.of(
      js.Array(
        KeyBinding.fromCommand(insertTab, new Key("Tab")),
        KeyBinding(_ => e.saveOrUpdate.runNow(), saveOrUpdate, true),
        KeyBinding(_ => e.saveOrUpdate.runNow(), saveOrUpdateAlt, true),
        KeyBinding(_ => e.openNewSnippetModal.runNow(), openNewSnippetModal, true),
        KeyBinding(_ => e.clear.runNow(), clear, true),
        KeyBinding(_ => e.clear.runNow(), clearAlt, true),
        KeyBinding(_ => e.toggleHelp.runNow(), help, true),
        KeyBinding(_ => e.toggleConsole.runNow(), console, true),
        KeyBinding(_ => e.formatCode.runNow(), format, true),
        KeyBinding(_ => presentationMode(e), presentation, true),
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

  def getName: String = {
    val macAdjusted = if (client.isMac) mac.replace("Meta", "Cmd") else default
    macAdjusted.replace("Escape", "Esc")
  }
}

object KeyBinding {
  def fromCommand(action: typings.codemirrorState.mod.StateCommand, key: Key, preventDefault: Boolean = false): JSKeyBinding = {
    JSKeyBinding()
      .setRun(x => action(x.asInstanceOf[anon.Dispatch]))
      .setKey(key.default)
      .setLinux(key.linux)
      .setMac(key.mac)
      .setWin(key.win)
      .setPreventDefault(preventDefault)
  }

  def apply(action: EditorView => Unit, key: Key, preventDefault: Boolean = false): JSKeyBinding = {
    JSKeyBinding()
      .setRun(editorView => { action(editorView); true })
      .setKey(key.default)
      .setLinux(key.linux)
      .setMac(key.mac)
      .setWin(key.win)
      .setPreventDefault(preventDefault)
  }
}
