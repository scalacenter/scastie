package com.olegych.scastie.client.components.editor

import com.olegych.scastie.api
import japgolly.scalajs.react._
import org.scalajs.dom
import typings.codemirrorAutocomplete.anon
import typings.codemirrorAutocomplete.mod._
import typings.codemirrorState.mod._
import typings.codemirrorView.mod._

import scala.collection.mutable.HashMap
import scala.concurrent.Future

import scalajs.js
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalajs.js.Thenable.Implicits._
import js.JSConverters._
import EditorTextOps._

trait MetalsAutocompletion extends MetalsClient with DebouncingCapabilities {
  val jsRegex = js.RegExp("\\.?\\w*")
  val selectionPattern = "\\$\\{\\d+:(.*?)\\}".r

  var wasPreviousIncomplete = true
  var previousWord = ""

  val completionInfoCache = HashMap.empty[String, dom.Node]

  /*
   * Creates additionalInsertInstructions e.g autoimport for completions
   */
  private def createAdditionalTextEdits(insertInstructions: List[api.AdditionalInsertInstructions], view: EditorView): Seq[ChangeSpec] = {
    insertInstructions.map(textEdit => {
      val editRange = textEdit.editRange
      val startPos = view.state.doc.line(editRange.startLine).from.toInt + editRange.startChar
      val endPos = view.state.doc.line(editRange.endLine).from.toInt + editRange.endChar
      js.Dynamic.literal(
        from = startPos,
        to = endPos,
        insert = textEdit.text
      ).asInstanceOf[ChangeSpec]
    })
  }

  private def prepareInsertionText(completion: api.CompletionItemDTO, lineStart: Int): (EditorSelection, String) = {
    val patternIndex = completion.instructions.text.indexOf("$0")
    val partiallyCleanedPattern = completion.instructions.text.replace("$0", "")
    val offset = if (patternIndex == -1) {
      partiallyCleanedPattern.length
    } else {
      patternIndex
    }
    val simpleSelection = EditorSelection.single(lineStart + offset)

    selectionPattern.findFirstMatchIn(partiallyCleanedPattern).map { regexMatch => {
      val offset = regexMatch.group(0).length - regexMatch.group(1).length
      val selection = EditorSelection.single(lineStart + regexMatch.start, lineStart + regexMatch.end - offset)
      val adjustedInsertString = partiallyCleanedPattern.substring(0, regexMatch.start) +
        regexMatch.group(1) +
        partiallyCleanedPattern.substring(regexMatch.end, partiallyCleanedPattern.length)

      (selection, adjustedInsertString)
    }}.getOrElse(simpleSelection, partiallyCleanedPattern)
  }

  /*
   * Creates edit transaction for completion. This enables cursor to be in proper possition after completion is accpeted
   */
  private def createEditTransaction(view: EditorView, completion: api.CompletionItemDTO, currentCursorPosition: Int): TransactionSpec = {
    val startLinePos = view.state.doc.line(completion.instructions.editRange.startLine).from
    val endLinePos = view.state.doc.line(completion.instructions.editRange.endLine).from
    val fromPos = startLinePos + completion.instructions.editRange.startChar
    val toPos = endLinePos + completion.instructions.editRange.endChar

    val newCursorStartLine = fromPos +
      completion.additionalInsertInstructions.foldLeft(0)(_ + _.text.length)

    val (selection, insertText) = prepareInsertionText(completion, newCursorStartLine.toInt)

    TransactionSpec().setChangesVarargs(
      (js.Dynamic.literal(
        from = fromPos.toDouble,
        to = toPos.toDouble max currentCursorPosition,
        insert = insertText
      ).asInstanceOf[ChangeSpec] +: createAdditionalTextEdits(completion.additionalInsertInstructions, view)):_*
    ).setSelection(selection)
  }

  type CompletionInfoF = js.Function1[Completion, js.Promise[dom.Node]]

  /*
   * Fetches documentation for selected completion
   */
  private def getCompletionInfo(completionItemDTO: api.CompletionItemDTO): CompletionInfoF = {
    val key = completionItemDTO.symbol.getOrElse(completionItemDTO.label)
    lazy val maybeCachedResult = completionInfoCache.get(key)
      .map(node => js.Promise.resolve[dom.Node](node))
      .getOrElse {
        makeRequest(api.CompletionInfoRequest(scastieMetalsOptions, completionItemDTO), "completionItemResolve")
          .map { maybeText =>
            parseMetalsResponse[String](maybeText).filter(_.nonEmpty).map { completionInfo =>
              val node = dom.document.createElement("div")
              node.innerHTML = InteractiveProvider.marked(completionInfo)
              completionInfoCache.put(key, node)
              node
            }.getOrElse(null)
          }.toJSPromise
      }

    val result: CompletionInfoF = (completion: Completion) => maybeCachedResult
    result
  }

  private val autocompletionTrigger = onChangeCallback((code, view) => {
    val matchesPreviousToken = view.matchesPreviousToken(previousWord)
    if (wasPreviousIncomplete || !matchesPreviousToken) startCompletion(view)
    if (!matchesPreviousToken) wasPreviousIncomplete = true
  })

  private val completionsF: js.Function1[CompletionContext, js.Promise[CompletionResult]] = {
    ctx => ifSupported {
      val word = ctx.matchBefore(jsRegex).asInstanceOf[anon.Text]

      if (!ctx.explicit || (word == null || word.text.isEmpty || (word.from == word.to))) {
        previousWord = ""
        Future.successful(null)
      } else {
        previousWord = word.text

        val request = toLSPRequest(ctx.state.doc.toString(), ctx.pos.toInt)
        val from = if (word.text.headOption == Some('.')) word.from + 1 else word.from

        makeRequest(request, "complete").map(maybeText =>
          parseMetalsResponse[api.ScalaCompletionList](maybeText).map { completionList =>
            val completions = completionList.items.map {
              case cmp @ api.CompletionItemDTO(name, detail, tpe, boost, insertInstructions, additionalInsertInstructions, symbol) =>
                Completion(name.stripSuffix(detail))
                  .setDetail(detail)
                  .setInfo(getCompletionInfo(cmp))
                  .setType(tpe)
                  .setBoost(-boost.getOrElse(-99).toDouble)
                  .setApplyFunction4((view, _, from, to) => {
                    wasPreviousIncomplete = false
                    Callback(view.dispatch(createEditTransaction(view, cmp, to.toInt)))
                  }
                  )
            }
            wasPreviousIncomplete = completionList.isIncomplete
            val result = CompletionResult(from, completions.toJSArray)
              .setValidForFunction4((_, _, _, _) => true)
            result
          }
        )
      }
    }.map(_.getOrElse(null)).toJSPromise
  }

  private val autocompletionConfig = CompletionConfig()
    .setInteractionDelay(0) // we want completions to work instantly
    .setOverrideVarargs(completionsF)
    .setActivateOnTyping(false) // we use our own autocompletion trigger with working debounce MetalsAutocompletion.autocompletionTrigger
    .setIcons(true)
    .setDefaultKeymap(true)

  def metalsAutocomplete: js.Array[Any] = js.Array[Any](
    autocompletion(autocompletionConfig),
    autocompletionTrigger,
  )
}
