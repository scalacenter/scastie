package com.olegych.scastie.client

import scala.io.AnsiColor

object AnsiColorFormatter extends AnsiColor {

  private val colors = Map(
    BLACK -> "ansi-color-black",
    RED -> "ansi-color-red",
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

  def formatToHtml(unformatted: String): String = {
    unformatted
      .foldLeft("" -> 0) {
        case ((_r, d), c) =>
          val r = _r + c
          val replaced = colors.collectFirst {
            case (ansiCode, replacement) if r.endsWith(ansiCode) =>
              if (ansiCode == RESET) r.replace(ansiCode, "</span>" * d) -> 0
              else r.replace(ansiCode, s"""<span class="$replacement">""") -> (d + 1)
          }
          replaced.getOrElse(r -> d)
      }
      ._1
  }
}
