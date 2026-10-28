package org.scastie.client

import scala.io.AnsiColor

object AnsiColorFormatter extends AnsiColor {

  private val colors = Map(
    BLACK -> "ansi-color-black",
    RED -> "ansi-color-red",
    "\u001b[90m" -> "ansi-color-gray",
    GREEN -> "ansi-color-green",
    YELLOW -> "ansi-color-yellow",
    BLUE -> "ansi-color-blue",
    MAGENTA -> "ansi-color-magenta",
    CYAN -> "ansi-color-cyan",
    WHITE -> "ansi-color-white",
    BLACK_B -> "ansi-bg-color-black",
    RED_B -> "ansi-bg-color-red",
    GREEN_B -> "ansi-bg-color-green",
    YELLOW_B -> "ansi-bg-color-yellow",
    BLUE_B -> "ansi-bg-color-blue",
    MAGENTA_B -> "ansi-bg-color-magenta",
    CYAN_B -> "ansi-bg-color-cyan",
    WHITE_B -> "ansi-bg-color-white",
    RESET -> "",
    BLINK -> "ansi-blink",
    BOLD -> "ansi-bold",
    REVERSED -> "ansi-reversed",
    INVISIBLE -> "ansi-invisible"
  )

  private val ansiCode = "\u001b\\[\\d+m".r

  private def applyCode(open: List[String], code: String): (String, List[String]) =
    if (code == RESET) ("</span>" * open.size, Nil)
    else
      colors.get(code) match {
        case Some(cls) => (spanStart(cls), cls :: open)
        case None      => (code, open)
      }

  private def spanStart(cls: String): String = s"""<span class="$cls">"""

  def openAfter(text: String, openBefore: List[String]): List[String] =
    ansiCode
      .findAllMatchIn(text)
      .foldLeft(openBefore)((open, m) => applyCode(open, m.matched)._2)
      .distinctBy(cssProperty)

  private def cssProperty(cls: String): String =
    if (cls.startsWith("ansi-color-")) "color"
    else if (cls.startsWith("ansi-bg-color-")) "background-color"
    else cls

  def formatToHtml(unformatted: String, openBefore: List[String] = Nil): String = {
    val reopened = openBefore.reverseIterator.map(spanStart).toVector
    val (parts, open, end) =
      ansiCode.findAllMatchIn(unformatted).foldLeft((reopened, openBefore, 0)) {
        case ((parts, open, end), m) =>
          val (html, nextOpen) = applyCode(open, m.matched)
          (parts :+ unformatted.substring(end, m.start) :+ html, nextOpen, m.end)
      }
    (parts :+ unformatted.substring(end) :+ "</span>" * open.size).mkString
  }
}
