package org.scastie.runtime.api

import scala.annotation.switch

object StringUtils {

  private def escapedChar(ch: Char): String = (ch: @switch) match {
    case '\b' => "\\b"
    case '\t' => "\\t"
    case '\n' => "\\n"
    case '\f' => "\\f"
    case '\r' => "\\r"
    case '"'  => "\\\""
    // case '\'' => "\\\'"
    case '\\' => "\\\\"
    case _    => if (ch.isControl) f"${"\\"}u${ch.toInt}%04x" else String.valueOf(ch)
  }

  implicit class EscapedString(val s: String) {
    def escaped: String = s.flatMap(escapedChar)
  }

}
