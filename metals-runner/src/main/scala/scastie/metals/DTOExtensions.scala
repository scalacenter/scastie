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

      val contentToOffset = offsetParams.content.take(offsetParams.offset).linesWithSeparators
      val line            = contentToOffset.size - 1

      val (content, position) =
        if offsetParams.isWorksheetMode then
          val adjustedContent  = s"""$wrapperObject${offsetParams.content.replace("\n", "\n" + wrapperIndent)}}"""
          val adjustedPosition = wrapperObject.length + line * 2 + offsetParams.offset
          (adjustedContent, adjustedPosition)
        else (offsetParams.content, offsetParams.offset)

      new CompilerOffsetParams(noSourceFilePath.toUri, content, position, EmptyCancelToken, java.util.Optional.empty())
    }

}
