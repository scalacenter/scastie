package org.scastie.instrumentation

import RuntimeConstants._
import org.scastie.api.ScalaTarget
import org.scastie.api.ScalaCli

case class PositionMapper private (
  private val lineMapping: Int => Int,
  private val columnOffsetMapping: Int => Int
) {

  /**
    * Maps a line from instrumented code back to the original user code.
    *
    * @param line
    *   the line number in the instrumented code (1-based)
    * @return
    *   the corresponding line number in the original user code (1-based)
    */
  def mapLine(line: Int): Int = {
    lineMapping(line)
  }

  /**
    * Maps a column from instrumented code back to the original user code, taking into account any column offsets
    *
    * @param line
    *   the line number in the instrumented code (1-based)
    * @param column
    *   the column number in the instrumented code (1-based)
    * @return
    *   the corresponding column number in the original user code (1-based)
    */
  def mapColumn(line: Int, column: Int): Int = {
    val offset = columnOffsetMapping(line)
    math.max(1, column - offset)
  }

}

object PositionMapper {

  private val instrumentationPrefix: String = "val $t = "
  private val prefixOffset: Int             = instrumentationPrefix.length

  /**
    * Creates a PositionMapper that maps positions from instrumented Scastie code back to original code.
    *
    * @param instrumentedCode
    *   the code after Scastie instrumentation has been applied
    * @param isScalaCli
    *  whether the target is Scala CLI
    * @return
    *   a PositionMapper for mapping positions
    */
  def apply(instrumentedCode: String, isScalaCli: Boolean = false): PositionMapper = {
    val lines                              = instrumentedCode.split('\n')
    val (lineMapping, columnOffsetMapping) = calculateMappings(lines, isScalaCli)
    new PositionMapper(lineMapping, columnOffsetMapping)
  }

  private def calculateMappings(lines: Array[String], isScalaCli: Boolean): (Int => Int, Int => Int) = {
    case class State(
      userCodeLinesSeen: Int = 0,
      lineMappings: Map[Int, Int] = Map.empty,
      columnOffsets: Map[Int, Int] = Map.empty
    )

    val result = lines.zipWithIndex
      .foldLeft(State()) { case (State(userCodeLinesSeen, lineMappings, columnOffsets), (line, index)) =>
        val instrumentedLineNumber = index + 1
        val trimmed                = line.trim

        val columnOffset = if (isWrappedUserCode(trimmed)) prefixOffset else 0

        if (!isExperimentalImport(trimmed) && !isInstrumentationLine(trimmed, isScalaCli)) {
          val newCount = userCodeLinesSeen + 1
          State(
            newCount,
            lineMappings + (instrumentedLineNumber  -> newCount),
            columnOffsets + (instrumentedLineNumber -> columnOffset)
          )
        } else {
          val fallbackLine = if (userCodeLinesSeen > 0) userCodeLinesSeen else 1
          State(
            userCodeLinesSeen,
            lineMappings + (instrumentedLineNumber  -> fallbackLine),
            columnOffsets + (instrumentedLineNumber -> columnOffset)
          )
        }
      }

    val lineMapping: Int => Int =
      instrumentedLineNumber => result.lineMappings.getOrElse(instrumentedLineNumber, instrumentedLineNumber)

    val columnOffsetMapping: Int => Int =
      instrumentedLineNumber => result.columnOffsets.getOrElse(instrumentedLineNumber, 0)

    (lineMapping, columnOffsetMapping)
  }

  private def isInstrumentationLine(line: String, isScalaCli: Boolean): Boolean = {
    line.matches("""\$doc\.startStatement\(\d+,\s*\d+\);""") ||
    line.matches("""\$doc\.endStatement\(\);""") ||
    line.matches("""\$doc\.binder\(.+,\s*\d+,\s*\d+\);""") ||
    line == "scala.Predef.locally {" ||
    line == "$t}" ||
    line.startsWith(s"import $runtimePackage") ||
    line.startsWith(s"object $instrumentedObject extends ScastieApp with $instrumentationRecorderT") ||
    (line.trim.startsWith("//> using") && isScalaCli)
  }

  private def isWrappedUserCode(line: String): Boolean = {
    line.startsWith(instrumentationPrefix)
  }

  private def isExperimentalImport(line: String): Boolean = {
    val experimentalRegex = """^\s*import\s+language\.experimental\.[^\n]+""".r
    experimentalRegex.matches(line)
  }

}
