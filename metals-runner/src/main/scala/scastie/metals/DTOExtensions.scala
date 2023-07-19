package scastie.metals

import java.nio.file.Path
import scala.meta.internal.metals.CompilerOffsetParams
import scala.meta.internal.metals.EmptyCancelToken
import scala.reflect.internal.util.NoSourceFile

import com.olegych.scastie.api.ScastieOffsetParams

object DTOExtensions {
  val wrapperIndent = "  "

  extension (offsetParams: ScastieOffsetParams)

    def toOffsetParams: CompilerOffsetParams = {
      val noSourceFilePath = Path.of(NoSourceFile.path)

      val wrapperObject = s"""|object worksheet {
                              |$wrapperIndent""".stripMargin

      val (content, position) =
        if offsetParams.isWorksheetMode then
          val (userDirectives, userCode) = offsetParams.content.split("\n").span(_.startsWith("//>"))

          val userDirectivesEndingWithLR = if (userDirectives.size == 0) then "" else userDirectives.mkString("", "\n", "\n")

          val adjustedContent = s"""${userDirectives.mkString("\n")}\n$wrapperObject${userCode.mkString("\n" + wrapperIndent)}}"""

          val userDirectivesLength = userDirectives.map(_.length + 1).sum
          if (offsetParams.offset < userDirectivesLength) then
            // cursor is in directives
            (adjustedContent, offsetParams.offset)
          else
            // cursor is in code
            val lines = userCode.take(offsetParams.offset - userDirectivesLength).linesWithSeparators
            (adjustedContent, wrapperObject.length + offsetParams.offset + lines.size * wrapperIndent.length)

        else (offsetParams.content, offsetParams.offset)

      new CompilerOffsetParams(noSourceFilePath.toUri, content, position, EmptyCancelToken, java.util.Optional.empty())
    }

}
