package com.olegych.scastie.instrumentation

case class DiffFailure(title: String,
                       expected: String,
                       obtained: String,
                       diff: String)
    extends Exception(title + "\n" + Diff.error2message(obtained, expected))

object Diff {
  def error2message(obtained: String, expected: String): String = {
    val sb = new StringBuilder

    sb.append("\n")

    sb.append(s"""
       ## Obtained
       #${trailingSpace(obtained)}
       """.stripMargin('#'))

    sb.append(s"""
       ## Expected
       #${trailingSpace(expected)}
       """.stripMargin('#'))

    sb.append(s"""
       ## Diff
       #${trailingSpace(compareContents(obtained, expected))}
         """.stripMargin('#'))
    sb.toString()
  }

  def assertNoDiff(obtained: String,
                   expected: String,
                   title: String = ""): Unit = {
    val result = compareContents(obtained, expected)
    if (result.nonEmpty) {
      throw DiffFailure(title, expected, obtained, result)
    }
  }

  private def trailingSpace(str: String): String = str.replaceAll(" \n", "∙\n")

  def compareContents(original: String, revised: String): String = {
    compareContents(original.replaceAllLiterally("\r\n", "\n").trim.split("\n"),
                    revised.replaceAllLiterally("\r\n", "\n").trim.split("\n"))
  }

  private def compareContents(original: Seq[String],
                              revised: Seq[String]): String = {
    import collection.JavaConverters._
    val diff = difflib.DiffUtils.diff(original.asJava, revised.asJava)
    if (diff.getDeltas.isEmpty) ""
    else
      difflib.DiffUtils
        .generateUnifiedDiff(
          "original",
          "revised",
          original.asJava,
          diff,
          1
        )
        .asScala
        .mkString("\n")
  }
}
