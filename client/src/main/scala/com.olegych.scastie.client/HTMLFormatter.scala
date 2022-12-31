package com.olegych.scastie.client

object HTMLFormatter {
  private val escapeMap =
    Map('&' -> "&amp;", '"' -> "&quot;", '<' -> "&lt;", '>' -> "&gt;")

  private def escape(text: String): String =
    text.iterator
      .foldLeft(new StringBuilder()) { (s, c) =>
        escapeMap.get(c) match {
          case Some(str)                                   => s ++= str
          case _ if c >= ' ' || "\n\r\t\u001b".contains(c) => s += c
          case _                                           => s // noop
        }
      }
      .toString

  def format(notEscapedAndUnformatted: String) = {
    val escaped = escape(notEscapedAndUnformatted)
    AnsiColorFormatter.formatToHtml(escaped)
  }
}
