package com.olegych.scastie.instrumentation

/**
  * Maps line numbers from Scastie instrumented code back to original user code lines.
  *
  * This class handles the mapping between code that has been processed by Scastie's instrumentation (which wraps
  * expressions in `scala.Predef.locally` blocks with binders for worksheet output) and the original user code.
  *
  * @param lineMapping
  *   function that maps instrumented line numbers to original line numbers
  */
class LineMapper private (
  lineMapping: Int => Int
) {

  /**
    * Maps a line number from instrumented code to the corresponding line in original code.
    *
    * @param instrumentedLine
    *   line number in the instrumented code
    * @return
    *   corresponding line number in the original code
    */
  def toOriginalLine(instrumentedLine: Int): Int = lineMapping(instrumentedLine)
}

object LineMapper {

  /**
    * Creates a LineMapper that maps lines from instrumented Scastie code back to original code.
    *
    * @param originalCode
    *   the original user code before instrumentation
    * @param instrumentedCode
    *   the code after Scastie instrumentation has been applied
    * @return
    *   a LineMapper instance for mapping line numbers
    */
  def apply(originalCode: String, instrumentedCode: String): LineMapper = {
    val mapping = buildSequentialMapping(instrumentedCode)
    new LineMapper(mapping)
  }

  private def buildSequentialMapping(instrumentedCode: String): Int => Int = {
    val lines    = instrumentedCode.split('\n')
    val mappings = calculateSequentialMappings(lines)

    instrumentedLineNumber => mappings.getOrElse(instrumentedLineNumber, instrumentedLineNumber)
  }

  private def calculateSequentialMappings(lines: Array[String]): Map[Int, Int] = {

    case class State(userCodeLinesSeen: Int = 0, mappings: Map[Int, Int] = Map.empty)

    lines.zipWithIndex
      .foldLeft(State()) { case (State(userCodeLinesSeen, mappings), (line, index)) =>
        val instrumentedLineNumber = index + 1
        val trimmed                = line.trim

        if (!isExperimentalImport(trimmed) && !isInstrumentationLine(trimmed)) {
          val newCount = userCodeLinesSeen + 1
          State(newCount, mappings + (instrumentedLineNumber -> newCount))
        } else {
          val fallbackLine = if (userCodeLinesSeen > 0) userCodeLinesSeen else 1
          State(userCodeLinesSeen, mappings + (instrumentedLineNumber -> fallbackLine))
        }
      }
      .mappings
  }

  private def isInstrumentationLine(line: String): Boolean = {
    line.matches("""\$doc\.startStatement\(\d+,\s*\d+\);""") ||
    line.matches("""\$doc\.endStatement\(\);""") ||
    line.matches("""\$doc\.binder\(.+,\s*\d+,\s*\d+\);""") ||
    line == "scala.Predef.locally {" ||
    line == "$t}" ||
    line.startsWith("import _root_.com.olegych.scastie.api.runtime") ||
    line.startsWith("object Playground extends ScastieApp with _root_.com.olegych.scastie.api.InstrumentationRecorder {")
  }

  private def isWrappedUserCode(line: String): Boolean = {
    line.startsWith("val $t = ") &&
    line.endsWith(";")
  }

  private def isExperimentalImport(line: String): Boolean = {
    val experimentalRegex = """^\s*import\s+language\.experimental\.[^\n]+""".r
    experimentalRegex.matches(line)
  }

}
