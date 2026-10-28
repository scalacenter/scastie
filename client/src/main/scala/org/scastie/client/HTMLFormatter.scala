package org.scastie.client

object HTMLFormatter {
  private val escapeMap =
    Map('&' -> "&amp;", '"' -> "&quot;", '<' -> "&lt;", '>' -> "&gt;")

  private def escape(text: String): String =
    text.iterator
      .foldLeft(new StringBuilder()) { (s, c) =>
        escapeMap.get(c) match {
          case Some(str)          => s ++= str
          case _ if !isDropped(c) => s += c
          case _                  => s // noop
        }
      }
      .toString

  private def isDropped(c: Char): Boolean = c < ' ' && !"\n\r\t\u001b".contains(c)

  def format(notEscapedAndUnformatted: String, openBefore: List[String] = Nil) = {
    val escaped = escape(notEscapedAndUnformatted)
    AnsiColorFormatter.formatToHtml(escaped, openBefore)
  }

  def openAfter(notEscapedAndUnformatted: String, openBefore: List[String]): List[String] =
    if (notEscapedAndUnformatted.indexOf('\u001b') < 0) openBefore
    else {
      val kept =
        if (notEscapedAndUnformatted.exists(isDropped)) notEscapedAndUnformatted.filterNot(isDropped)
        else notEscapedAndUnformatted
      AnsiColorFormatter.openAfter(kept, openBefore)
    }
}
