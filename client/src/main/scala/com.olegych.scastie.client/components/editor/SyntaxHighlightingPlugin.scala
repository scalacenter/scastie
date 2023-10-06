package com.olegych.scastie.client.components.editor

import typings.webTreeSitter.mod._
import org.scalablytyped.runtime.StObject
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import japgolly.scalajs.react._
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._
import scalajs.js
import org.scalajs.dom

class SyntaxHighlightingPlugin(editorView: hooks.Hooks.UseStateF[CallbackTo, EditorView]) {
  val syntaxHighlightingExtension = new Compartment()
  val fallbackExtension = typings.codemirrorLanguage.mod.StreamLanguage.define(typings.codemirrorLegacyModes.modeClikeMod.scala_).extension

  val location = dom.window.location
  // this is workaround until we migrate all services to proper docker setup or unify the servers
  val apiBase = if (location.hostname == "localhost") {
    location.protocol ++ "//" ++ location.hostname + ":" ++ "8080"
  } else if (location.protocol == "file:") {
    "http://localhost:8080"
  } else {
    "https://scastie.scala-lang.org"
  }


  val initOptions = new js.Object {
    val apiBaseField = apiBase
    def locateFile(scriptName: String, scriptDirectory: String): String =
      s"$apiBaseField/public/tree-sitter.wasm"
  }

  private val fetchTSWasm = init(initOptions)
    .toFuture
    .flatMap(_ => Language.load(s"$apiBase/public/tree-sitter-scala.wasm").toFuture)

  fetchTSWasm.map { language =>
    val parser = new TreesitterParser()
    parser.setLanguage(language)
    switchToTreesitterParser(parser, language)
  }

  def switchToTreesitterParser(scalaParser: Parser, language: Language): Unit = {
    val extension = ViewPlugin.define(editorView =>
      new SyntaxHighlightingHandler(scalaParser, language, editorView.state.doc.toString),
      PluginSpec[SyntaxHighlightingHandler]().setDecorations(_.decorations)
    ).extension

    val effects = syntaxHighlightingExtension.reconfigure(extension)
    val transactionSpec = TransactionSpec().setEffects(effects)
    editorView.modState(editorView => {
        editorView.dispatch(transactionSpec)
        editorView
    }).runNow()
  }
}
