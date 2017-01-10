package instrumentation

import java.nio.file._

import scala.collection.JavaConverters._

import System.{lineSeparator => nl}

object InstrumentSpecs {

  case class DiffFailure(title: String,
                         expected: String,
                         obtained: String,
                         diff: String) {
    override def toString = title + nl + error2message(obtained, expected)
  }

  def error2message(obtained: String, expected: String): String = {
    val sb = new StringBuilder
    if (obtained.length < 1000) {
      sb.append(s"""
         ## Obtained
         #${trailingSpace(obtained)}
         """.stripMargin('#'))
    }
    sb.append(s"""
       ## Diff
       #${trailingSpace(compareContents(obtained, expected))}
         """.stripMargin('#'))
    sb.toString()
  }

  def assertNoDiff(obtained: String,
                   expected: String,
                   title: String = ""): Option[DiffFailure] = {
    val result = compareContents(obtained, expected)
    if (result.isEmpty) None
    else {
      Some(DiffFailure(title, expected, obtained, result))
    }
  }

  def trailingSpace(str: String): String = str.replaceAll(" \n", "∙\n")

  def compareContents(original: String, revised: String): String = {
    compareContents(original.trim.split(nl), revised.trim.split(nl))
  }

  def compareContents(original: Seq[String], revised: Seq[String]): String = {
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
        .drop(3)
        .mkString(nl)
  }

  private def slurp(path: Path): String = {
    Files.readAllLines(path).toArray.mkString(nl)
  }

  def main(args: Array[String]): Unit = {
    val tests = {
      val path = Paths.get("instrumentation", "src", "test", "resources")
      val s    = Files.newDirectoryStream(path)
      val t    = s.asScala.toList
      s.close()
      t
    }

    val results =
      tests
        .map(
          p =>
            (
              p,
              slurp(p.resolve("original.scala")),
              slurp(p.resolve("instrumented.scala"))
          ))
        .map {
          case (path, original, instrumented) =>
            val out = Instrument(original)

            assertNoDiff(out, instrumented) match {
              case Some(diff) =>
                "✘" + nl +
                  diff.toString
              case None => "✓"
            }
        }

    println(results.mkString(System.lineSeparator))
  }
}
