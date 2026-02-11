package org.scastie.metals

import java.nio.file.Path
import scala.meta.internal.metals.CompilerOffsetParams
import scala.meta.internal.metals.EmptyCancelToken
import scala.reflect.internal.util.NoSourceFile

import org.scastie.api.ScastieOffsetParams
import scala.meta.pc.VirtualFileParams
import java.net.URI
import scala.meta.pc.CancelToken
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.CodeAction
import org.scastie.api._
import scala.jdk.CollectionConverters._
import org.scastie.metals.JavaConverters._

object DTOExtensions {
  val wrapperIndent = "  "
  val noSourceFile= Path.of(NoSourceFile.path).toUri()

  private def diagSeverityToSeverity(severity: DiagnosticSeverity): Severity = {
    if (severity == DiagnosticSeverity.Error) Error
    else if (severity == DiagnosticSeverity.Information) Info
    else if (severity == DiagnosticSeverity.Hint) Info
    else if (severity == DiagnosticSeverity.Warning) Warning
    else Error
  }

  extension (diagnostic: Diagnostic)

    def toProblem(isWorksheetMode: Boolean): Problem =
      val worksheetLineOffset = if isWorksheetMode then 1 else 0 // wrapper object
      val worksheetOffset = if isWorksheetMode then wrapperIndent.length else 0 // wrapper indent

      val actions: Option[List[ScalaAction]] = Option(diagnostic.getData())
        .collect { case wrapper: java.util.List[_] => wrapper }
        .map { list =>
          list.asScala.toList.collect {
            case codeAction: CodeAction =>
              codeAction.toScalaAction(worksheetOffset, worksheetLineOffset)
          }
        }
        .filter(_.nonEmpty)

      /* Diags are 1-based in editor but in compler they are 0-based, thus magic + 1 */
      Problem(
        diagSeverityToSeverity(diagnostic.getSeverity()),
        Option(diagnostic.getRange().getStart().getLine() - worksheetLineOffset + 1),
        Option(diagnostic.getRange().getStart().getCharacter() - worksheetOffset + 1),
        Option(diagnostic.getRange().getEnd().getCharacter() - worksheetOffset + 1),
        diagnostic.getMessage(),
        actions
      )

  extension (offsetParams: ScastieOffsetParams)

    def toDiagnosticParams: VirtualFileParams =
      val (instrumentedParams, insideWrapper) = toOffsetParams
      new VirtualFileParams {
        override def text(): String = instrumentedParams.text
        override def uri(): URI = noSourceFile
        override def token(): CancelToken = EmptyCancelToken
        override def shouldReturnDiagnostics(): Boolean = true
      }

    def toOffsetParams: (CompilerOffsetParams, Boolean) = {

      val (content, position, insideWrapper) =
        if !offsetParams.isWorksheetMode then (offsetParams.content, offsetParams.offset, false)
        else

          val (usingDirectivesLines, remainingLines) = offsetParams.content.linesWithSeparators.span(l => l.startsWith("//") || l.isBlank())

          val (usingDirectives, remainingCode) = (usingDirectivesLines.mkString, remainingLines.mkString)
          val wrapperObject = s"""|object worksheet {
                                  |$wrapperIndent""".stripMargin

          val adjustedContent = s"""$usingDirectives$wrapperObject${remainingCode.replace("\n", "\n" + wrapperIndent)}}"""

          /* If we're before using directives, we're inside it, return everything default */
          if (offsetParams.offset < usingDirectives.length) then
            (adjustedContent, offsetParams.offset, false)
          else
            val offsetWithoutDirectives = offsetParams.offset - usingDirectives.length
            val contentUntilOffset = remainingCode.take(offsetWithoutDirectives).linesWithSeparators
            val line            = contentUntilOffset.size - 1 // 0 based thats why - 1
            val adjustedPosition = wrapperObject.length + line * 2 + offsetParams.offset

            (adjustedContent, adjustedPosition, true)

      (CompilerOffsetParams(noSourceFile, content, position, EmptyCancelToken, java.util.Optional.empty()), insideWrapper)
    }

}
