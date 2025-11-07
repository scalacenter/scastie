package org.scastie.client.components.editor

import typings.webTreeSitter.mod.{Parser, Language, Query}

class SyntaxHighlighter(parser: Parser, language: Language, query: Query) {
  parser.setLanguage(language)

  def highlightScalaLabel(label: String): String = {
    val tree = parser.parse(label)
    val captures = query.captures(tree.rootNode)

    val indexedCaptures = captures.map { capture =>
      val start = capture.node.startIndex.toInt
      val end = capture.node.endIndex.toInt
      val cls = capture.name.replace(".", "-")
      (start, end, cls)
    }

    val (result, lastIdx) = indexedCaptures.foldLeft((List.empty[String], 0)) {
      case ((acc, last), (start, end, cls)) =>
        val before = if (start > last) label.substring(last, start) else ""
        val highlighted = s"<span class='$cls'>${label.substring(start, end)}</span>"
        (acc :+ before :+ highlighted, end)
    }

    val tail = if (lastIdx < label.length) label.substring(lastIdx) else ""
    (result :+ tail).mkString("")
  }
}