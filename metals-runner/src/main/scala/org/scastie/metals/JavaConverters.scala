package org.scastie.metals

import scala.jdk.CollectionConverters._
import scala.meta.internal.pc.CompletionItemData

import com.google.gson.Gson
import org.scastie.api._
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

  extension (item: SignatureHelp)
    def toSignatureHelpDTO =
      val signatures = item.getSignatures().asScala.map { sig =>
        val params = sig.getParameters().asScala.map { param =>
          val doc = Option(param.getDocumentation).fold("")(_.toDocstring)
          ParameterInformationDTO(
            param.getLabel().toString,
            doc
          )
        }.toList
        val doc = Option(sig.getDocumentation).fold("")(_.toDocstring)
        SignatureInformationDTO(sig.getLabel(), doc, params)
      }.toList
      SignatureHelpDTO(
        signatures,
        item.getActiveSignature(),
        item.getActiveParameter()
      )

  extension (range: Range) {

    def toScalaRange(insideWrapper: Boolean) =
      val start      = range.getStart()
      val end        = range.getEnd()
      val lineOffset = if insideWrapper then 0 else 1
      val charOffset = if insideWrapper then -DTOExtensions.wrapperIndent.length else 0
      EditRange(
        start.getLine + lineOffset,
        start.getCharacter + charOffset,
        end.getLine + lineOffset,
        end.getCharacter + charOffset
      )

  }

  extension (completions: CompletionList)

    def toScalaCompletionList(isWorksheetMode: Boolean, insideWrapper: Boolean): ScalaCompletionList = {

      def createInsertInstructions(completion: CompletionItem): Option[InsertInstructions] = {
        completion.getTextEdit().asScala match {
          case Left(textEdit) => Some(
              InsertInstructions(
                textEdit.getNewText,
                textEdit.getRange.toScalaRange(insideWrapper),
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
          AdditionalInsertInstructions(
            edit.getNewText(),
            edit.getRange().toScalaRange(false)
          )
        })
        .toList

      val completionItems =
        for {
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
        )
      ScalaCompletionList(completionItems.toSet, completions.isIncomplete)
    }

  def parseCompletionData(completion: CompletionItem): Option[String] =
    Option(completion.getData()).map { completionData =>
      gson.fromJson(completionData.toString, classOf[CompletionItemData]).symbol
    }

  extension (codeAction: CodeAction)
    def toScalaAction(worksheetOffset: Int, worksheetLineOffset: Int): ScalaAction = {
      val edit = Option(codeAction.getEdit).map { workspaceEdit =>
        val allChanges = Option(workspaceEdit.getChanges)
          .map { changesMap =>
            changesMap.asScala.values.flatMap(_.asScala).toList
          }
          .getOrElse(List.empty)

        val scalaTextEdits = allChanges.map { textEdit =>
          val lspRange = textEdit.getRange
          val start = lspRange.getStart
          val end = lspRange.getEnd

          val adjustedStartLine = start.getLine - worksheetLineOffset
          val adjustedStartChar = start.getCharacter - worksheetOffset
          val adjustedEndLine = end.getLine - worksheetLineOffset
          val adjustedEndChar = end.getCharacter - worksheetOffset

          val range = DiagnosticRange(
            start = DiagnosticPosition(adjustedStartLine, adjustedStartChar),
            end = DiagnosticPosition(adjustedEndLine, adjustedEndChar)
          )
          ScalaTextEdit(range, textEdit.getNewText)
        }

        ScalaWorkspaceEdit(scalaTextEdits)
      }

      ScalaAction(
        title = codeAction.getTitle,
        description = Option(codeAction.getKind),
        edit = edit
      )
    }

}
