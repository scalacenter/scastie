package org.scastie.client.components.editor

import org.scalajs.dom
import org.scastie.api
import org.scastie.client
import scalajs.js
import typings.codemirrorAutocomplete.mod.acceptCompletion
import typings.codemirrorCommands.mod._
import typings.codemirrorState.anon
import typings.codemirrorState.mod._
import typings.codemirrorState.mod.TransactionSpec
import typings.codemirrorView.mod.{KeyBinding => JSKeyBinding}
import typings.codemirrorView.mod.EditorView
import typings.replitCodemirrorVim.mod.Vim_

object EditorKeymaps {

  private def presentationMode(editor: CodeEditor): Unit = {
    if (!editor.isEmbedded) {
      editor.togglePresentationMode.runNow()
      if (!editor.isPresentationMode) {
        dom.window.alert("Press F8 again to leave the presentation mode")
      }
    }
  }

  def registerVimCommands(e: CodeEditor): Unit = {
    Vim_.defineEx(
      "w",
      "",
      (_, _) => e.saveOrUpdate.runNow()
    )
    Vim_.defineEx(
      "run",
      "",
      (_, _) => e.saveOrUpdate.runNow()
    )
    Vim_.defineEx(
      "f",
      "",
      (_, _) => e.formatCode.runNow()
    )
    Vim_.defineEx(
      "format",
      "",
      (_, _) => e.formatCode.runNow()
    )
    Vim_.defineEx(
      "c",
      "",
      (_, _) => e.clear.runNow()
    )
    Vim_.defineEx(
      "clear",
      "",
      (_, _) => e.clear.runNow()
    )
    Vim_.defineEx(
      "h",
      "",
      (_, _) => e.toggleHelp.runNow()
    )
    Vim_.defineEx(
      "help",
      "",
      (_, _) => e.toggleHelp.runNow()
    )
  }

  val saveOrUpdate        = new Key("Ctrl-Enter", "Meta-Enter")
  val saveOrUpdateAlt     = new Key("Ctrl-s", "Meta-s")
  val openNewSnippetModal = new Key("Ctrl-m", "Meta-m")
  val clear               = new Key("Escape")
  val clearAlt            = new Key("F1")
  val console             = new Key("Ctrl-j", "Meta-j")
  val help                = new Key("F5")
  val format              = new Key("F6")
  val presentation        = new Key("F8")

  def setupGlobalKeybinds(e: CodeEditor): Unit = {
    val handleGlobalKeydown = (event: dom.KeyboardEvent) => {
      val actions: List[(Key, () => Unit)] = List(
        console -> (() => e.toggleConsole.runNow())
      )

      actions.collectFirst {
        case (key, action) if key.matches(event) => action
      }.foreach { action =>
        event.preventDefault()
        action()
      }
    }

    dom.document.addEventListener("keydown", handleGlobalKeydown)
  }

  def keymapping(e: CodeEditor) = {
    val base = js.Array(
      KeyBinding.tabKeybind,
      KeyBinding(_ => e.saveOrUpdate.runNow(), saveOrUpdate, true),
      KeyBinding(_ => e.saveOrUpdate.runNow(), saveOrUpdateAlt, true),
      KeyBinding(_ => e.openNewSnippetModal.runNow(), openNewSnippetModal, true),
      KeyBinding(_ => e.clear.runNow(), clearAlt, true),
      KeyBinding(_ => e.toggleHelp.runNow(), help, true),
      KeyBinding(_ => e.formatCode.runNow(), format, true),
      KeyBinding(_ => presentationMode(e), presentation, true)
    )
    if (e.editorMode != api.Vim) {
      base.push(KeyBinding(_ => e.clear.runNow(), clear, true))
    }
    typings.codemirrorView.mod.keymap.of(base)
  }

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

  def getKey: String = {
    if (client.isMac) mac else default
  }

  def matches(event: dom.KeyboardEvent): Boolean = {
    val keyString = getKey.toLowerCase
    val ctrl = event.ctrlKey
    val meta = event.metaKey
    val shift = event.shiftKey
    val alt = event.altKey
    val key = event.key.toLowerCase

    val parts = keyString.split("-").toList
    val (requiredMods, mainKey) = (parts.init.toSet, parts.last)

    val presentMods = List(
      if (ctrl) "ctrl" else "",
      if (meta) "meta" else "",
      if (shift) "shift" else "",
      if (alt) "alt" else ""
    ).filter(_.nonEmpty).toSet

    presentMods == requiredMods && key == mainKey
  }
}

object KeyBinding {

  val tabKeybind: JSKeyBinding = {
    val key = new Key("Tab")
    JSKeyBinding()
      .setRun(view =>
        if (!acceptCompletion(view)) {
          view.dispatch(
            TransactionSpec()
              .setChanges(
                js.Dynamic.literal(from = view.state.selection.main.head, insert = "  ").asInstanceOf[ChangeSpec]
              )
              .setSelection(EditorSelection.single(view.state.selection.main.head + 2))
          )
          true
        } else false
      )
      .setKey(key.default)
      .setLinux(key.linux)
      .setMac(key.mac)
      .setWin(key.win)
      .setPreventDefault(true)
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
