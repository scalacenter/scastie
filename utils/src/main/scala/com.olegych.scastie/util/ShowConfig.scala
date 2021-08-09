package com.olegych.scastie.util

import com.typesafe.config.{Config, ConfigRenderOptions}
import scala.collection.mutable

object ShowConfig {
  /** Helper function to show config only at `paths` */
  def apply(c: Config, paths: String): String = {
    val groups = mutable.Stack.empty[String]

    def valueAt(path: String) = {
      val fullPath = (groups :+ path.trim).mkString(".")
      c.getValue(fullPath).render(opt)
    }

    paths.linesIterator.map {
      // empty or comment
      case s if s.trim.isEmpty || s.trim.startsWith("#") =>
        s
      // start a new group
      case s if s.trim.endsWith("{") =>
        groups.push(s.trim.dropRight(1).trim)
        s
      // end prev group
      case s if s.trim.startsWith("}") =>
        groups.pop()
        s
      // override
      case s if s.contains(':') || s.contains('=') =>
        val Array(path, newValue) = s.split(Array(':', '='))
        val value = valueAt(path)
        if (newValue.trim == value) s
        else s"$s # Overridden. Old value = $value"
      // normal path
      case s =>
        val leadingSpaces = "\n" + s.takeWhile(_ == ' ')
        val value = valueAt(s).linesIterator.mkString(leadingSpaces).trim
        s"$s: $value"
    }.mkString("\n")
  }

  private val opt = ConfigRenderOptions.concise().setFormatted(true)
}
