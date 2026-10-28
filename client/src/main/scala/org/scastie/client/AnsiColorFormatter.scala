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

  def formatToHtml(unformatted: String): String = {
    val (parts, _, end) =
      ansiCode.findAllMatchIn(unformatted).foldLeft((Vector.empty[String], 0, 0)) {
        case ((parts, openSpans, end), m) =>
          val (html, nextOpenSpans) =
            if (m.matched == RESET) ("</span>" * openSpans, 0)
            else
              colors.get(m.matched) match {
                case Some(cls) => (s"""<span class="$cls">""", openSpans + 1)
                case None      => (m.matched, openSpans)
              }
          (parts :+ unformatted.substring(end, m.start) :+ html, nextOpenSpans, m.end)
      }
    (parts :+ unformatted.substring(end)).mkString
  }
}
