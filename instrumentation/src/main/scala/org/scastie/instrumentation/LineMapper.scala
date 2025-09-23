package org.scastie.instrumentation

object LineMapper {

  /**
    * Creates a lineMapping that maps lines from instrumented Scastie code back to original code.
    *
    * @param instrumentedCode
    *   the code after Scastie instrumentation has been applied
    * @return
    *   a lineMapping function for mapping line numbers
    */
  def apply(instrumentedCode: String): Int => Int = {
    buildSequentialMapping(instrumentedCode)
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
    line.startsWith("import _root_.org.scastie.runtime") ||
    line.startsWith("object Playground extends ScastieApp with _root_.org.scastie.runtime.InstrumentationRecorder {") ||
    line.startsWith("//> using")
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
