package com.olegych.scastie.client.components.editor

import typings.webTreeSitter.mod._
import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalablytyped.runtime.StObject
import scala.scalajs.js.RegExp
import typings.codemirrorState.mod.ChangeSet
import typings.codemirror.mod

import japgolly.scalajs.react._
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._

import scalajs.js
import scala.concurrent.Future
import japgolly.scalajs.react.facade.performance
import scala.util.Failure
import scala.util.Success
import scala.collection.mutable.ListBuffer


class SyntaxHighlightingHandler(parser: Parser, language: Language, initialState: String) extends js.Object {
  var isLoaded = false

  val query = language.query(HighlightQuery.queryString)
  val queryCaptureNames = query.captureNames

  var tree = parser.parse(initialState)
  var decorations: DecorationSet = computeDecorations()

  private def computeDecorations(): DecorationSet = {
    val rangeSetBuilder = new RangeSetBuilder[Decoration]()

    val captures = query.captures(tree.rootNode)


    captures.foldLeft(Option.empty[QueryCapture]){ (previousCapture, currentCapture) =>
      if (!previousCapture.exists(_ == currentCapture)) {
        val startPosition = currentCapture.node.startIndex
        val endPosition = currentCapture.node.endIndex

        val mark = Decoration.mark(
          MarkDecorationSpec()
            .setInclusive(true)
            .setClass(currentCapture.name.replace(".", "-")))
        rangeSetBuilder.add(startPosition, endPosition, mark)

      }

      Some(currentCapture)

    }

    rangeSetBuilder.finish()
  }

  def indexToTSPoint(text: Text, index: Double): Point = {
    val line = text.lineAt(index)
    Point(index - line.from, line.number - 1)
  }

  def mapChangesToTSEdits(changes: ChangeSet, originalText: Text, newText: Text): List[Edit] = {
    val editBuffer = ListBuffer[Edit]()

    changes.iterChanges(
      (fromA: Double, toA: Double, fromB: Double, toB: Double, editText: Text) => {
        val oldEndPosition = indexToTSPoint(originalText, toA)
        val newEndPosition = indexToTSPoint(newText, toB)
        val startPosition = indexToTSPoint(originalText, fromA)
        editBuffer.addOne(Edit(toB, newEndPosition, toA, oldEndPosition, fromA, startPosition))
     }
    )

    editBuffer.toList

  }

  var update: js.Function1[ViewUpdate, Unit] = viewUpdate => {

    if (viewUpdate.docChanged) {
      val start = performance.now()
      val newText = viewUpdate.state.doc.toString
      val edits = mapChangesToTSEdits(viewUpdate.changes, viewUpdate.startState.doc, viewUpdate.state.doc)
      edits.foreach(tree.edit)
      tree = parser.parse(newText, tree)
      val changes = viewUpdate.changes
      val editor = viewUpdate.view
      decorations = computeDecorations()
      println(performance.now() - start)
    }
  }
}



object SyntaxHighlightingHandler {
  println("WTF")

  var isLoaded = false
  val syntaxHighlightingExtension = new Compartment()

  val fallbackExtension = typings.codemirrorLanguage.mod.StreamLanguage.define(typings.codemirrorLegacyModes.modeClikeMod.scala_).extension

  val parser = init(new js.Object {
    def locateFile(scriptName: String, scriptDirectory: String): String =
      "public/tree-sitter.wasm"
  }).toFuture.map { _ => new typings.webTreeSitter.mod.^() }


  val language = Language.load("public/tree-sitter-scala.wasm").toFuture

  val scalaParser = for {
    p <- parser
    l <- language
  } yield {
    p.setLanguage(l)
    p
  }

  def checkTreesitterStatus(): Boolean = {
    language.value.map {
      case Failure(exception) =>
        println(exception)
      case _ =>
    }

    scalaParser.isCompleted && language.isCompleted &&
      scalaParser.value.exists(_.isSuccess) && language.value.exists(_.isSuccess)
  }

  def switchToTreesitterParser(
    editorView: hooks.Hooks.UseStateF[CallbackTo, EditorView],
    props: CodeEditor
  ): Callback = Callback {
    println("CALLBACK STARTED")
    val combined = for {
      p <- scalaParser
      l <- language
    } yield p -> l

    (scalaParser zip language).map { case (parser, language) =>
      val extension = ViewPlugin.define(editorView =>
        new SyntaxHighlightingHandler(parser, language, editorView.state.doc.toString),
        PluginSpec[SyntaxHighlightingHandler]().setDecorations(_.decorations)
      ).extension

      isLoaded = true

      val effects = syntaxHighlightingExtension.reconfigure(extension)
      editorView.value.dispatch(TransactionSpec().setEffects(effects.asInstanceOf[StateEffect[Any]]))
    }

  }.when_(checkTreesitterStatus() && !isLoaded)
}

