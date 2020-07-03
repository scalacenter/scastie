package com.olegych.scastie.instrumentation

import com.olegych.scastie.util.ScastieFileUtil

case class DiffFailure(title: String, expected: String, obtained: String, diff: String)
    extends Exception(title + "\n" + Diff.error2message(obtained, expected))

object Diff {
  def error2message(obtained: String, expected: String): String = {
    ScastieFileUtil.write(new java.io.File("target/obtained.scala").toPath, obtained, truncate = true)
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

  def assertNoDiff(obtained: String, expected: String, title: String = ""): Unit = {
    val result = compareContents(obtained, expected)
    if (result.nonEmpty) {
      throw DiffFailure(title, expected, obtained, result)
    }
  }

  private def trailingSpace(str: String): String = str.replaceAll(" \n", "<trailing space>\n")

  def compareContents(obtained: String, expected: String): String = {
    compareContents(
      expected = expected.replace("\r\n", "\n").trim.split("\n").toList,
      obtained = obtained.replace("\r\n", "\n").trim.split("\n").toList,
    )
  }

  private def compareContents(expected: Seq[String], obtained: Seq[String]): String = {
    import scala.jdk.CollectionConverters._
    val diff = difflib.DiffUtils.diff(expected.asJava, obtained.asJava)
    if (diff.getDeltas.isEmpty) ""
    else
      difflib.DiffUtils
        .generateUnifiedDiff(
          "expected",
          "obtained",
          expected.asJava,
          diff,
          1
        )
        .asScala
        .mkString("\n")
  }
}
