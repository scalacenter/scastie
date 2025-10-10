package org.scastie.metals

import java.nio.file.Path
import scala.meta.internal.metals.CompilerOffsetParams
import scala.meta.internal.metals.EmptyCancelToken
import scala.reflect.internal.util.NoSourceFile

import org.scastie.api.ScastieOffsetParams

object DTOExtensions {
  val wrapperIndent = "  "

  extension (offsetParams: ScastieOffsetParams)

    def toOffsetParams: (CompilerOffsetParams, Boolean) = {
      val noSourceFilePath = Path.of(NoSourceFile.path)

      val (content, position, insideWrapper) =
        if offsetParams.isWorksheetMode then

          val (usingDirectivesLines, remainingLines) = offsetParams.content.linesWithSeparators.span:
            case line if line.startsWith("//>") => true
            case _                              => false

          val (usingDirectives, remainingCode) = (usingDirectivesLines.mkString, remainingLines.mkString)
          val wrapperObject = s"""|object worksheet {
                                  |$wrapperIndent""".stripMargin

          val adjustedContent =
            s"""$usingDirectives$wrapperObject${remainingCode.replace("\n", "\n" + wrapperIndent)}}"""

          if (offsetParams.offset < usingDirectives.length) then (adjustedContent, offsetParams.offset, false)
          else
            val offsetWithoutDirectives = offsetParams.offset - usingDirectives.length
            val contentToOffset = remainingCode.take(offsetWithoutDirectives).linesWithSeparators
            val line = contentToOffset.size - 1
            val adjustedPosition = wrapperObject.length + line * 2 + offsetParams.offset

            (adjustedContent, adjustedPosition, true)
        else (offsetParams.content, offsetParams.offset, false)

      (
        CompilerOffsetParams(noSourceFilePath.toUri, content, position, EmptyCancelToken, java.util.Optional.empty()),
        insideWrapper
      )
    }

}
