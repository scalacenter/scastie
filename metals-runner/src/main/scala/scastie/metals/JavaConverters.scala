package scastie.metals

import scala.jdk.CollectionConverters._
import scala.meta.internal.pc.CompletionItemData

import com.google.gson.Gson
import com.olegych.scastie.api._
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.jsonrpc.messages.{Either => JEither}
import org.slf4j.LoggerFactory

object JavaConverters {
  private val logger = LoggerFactory.getLogger(getClass)
  private val gson   = new Gson()

  extension [A, B](either: JEither[A, B])

    def asScala =
      if either.isLeft then Left[A, B](either.getLeft)
      else Right[A, B](either.getRight)

  extension (documentation: JEither[String, MarkupContent])
    def toDocstring = documentation.asScala.map(_.getValue).merge

  extension (documentation: JEither[String, MarkedString])
    def toHoverContent = documentation.asScala.map(_.getValue).merge

  extension (item: CompletionItem) def getDocstring = Option(item.getDocumentation).fold("")(_.toDocstring)

  extension (item: Hover)

    def toHoverDTO =
      val doc = Option(item.getContents).fold("")(_.getRight.getValue)
      HoverDTO(0, 0, doc)

  extension (completions: CompletionList)

    def toSimpleScalaList: Set[CompletionItemDTO] = (for {
      completion         <- completions.getItems().asScala
      filterText         <- Option(completion.getFilterText())
      detail             <- Option(completion.getDetail())
      kind               <- Option(completion.getKind()).map(_.toString.toLowerCase)
      order              <- Option(completion.getSortText()).map(_.toIntOption)
      insertInstructions <- createInsertInstructions(completion)
      additionalInsertInstructions = createAdditionalInsertInstructions(completion)
      data                         = parseCompletionData(completion)
    } yield CompletionItemDTO(
      filterText,
      detail,
      kind,
      order,
      insertInstructions,
      additionalInsertInstructions,
      data
    )).toSet

  def createInsertInstructions(completion: CompletionItem): Option[InsertInstructions] = {
    completion.getTextEdit().asScala match {
      case Left(textEdit) =>
        val move = textEdit.getNewText.indexOf("$0")
        Some(
          InsertInstructions(
            textEdit.getNewText.replace("$0", ""),
            if move > 0 then move else textEdit.getNewText.length
          )
        )
      case Right(insertReplace) =>
        logger.error("[InsertReplaceEdit] should not appear as it is not used in metals")
        None

    }
  }

  def createAdditionalInsertInstructions(completion: CompletionItem): List[AdditionalInsertInstructions] = Option(
    completion.getAdditionalTextEdits()
  ).map(_.asScala)
    .toList
    .flatten
    .map(edit => {
      val range = edit.getRange
      val start = range.getStart
      val end   = range.getEnd
      AdditionalInsertInstructions(
        edit.getNewText(),
        start.getLine + 1,
        start.getCharacter,
        end.getLine + 1,
        end.getCharacter
      )
    })
    .toList

  def parseCompletionData(completion: CompletionItem): Option[String] =
    Option(completion.getData()).map { completionData =>
      gson.fromJson(completionData.toString, classOf[CompletionItemData]).symbol
    }

}
