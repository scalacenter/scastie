package org.scastie.client.components.editor

import typings.webTreeSitter.mod.{Language, Parser, Query, SyntaxNode}

case class HighlightNode(start: Int, end: Int, cssClass: Option[String])

trait SyntaxHighlightable {
  def syntaxHighlighter: Option[SyntaxHighlighter]

  def highlight(code: String, activeParameter: Option[Int] = None): String = {
    syntaxHighlighter match {
      case Some(highlighter) => highlighter.highlight(code, activeParameter)
      case None =>
        val markdown = s"""```scala
                          |$code
                          |```""".stripMargin
        InteractiveProvider.renderMarkdown(markdown)
    }
  }
}

case class SyntaxHighlighter(parser: Parser, language: Language, query: Query) {

  parser.setLanguage(language)

  def highlight(label: String, activeParameter: Option[Int] = None): String = {
    val tree     = parser.parse(label)
    val captures = query.captures(tree.rootNode)

    val paramRegex   = "([a-zA-Z0-9_]+:\\s*[a-zA-Z0-9_\\[\\]]+)".r
    val paramMatches = paramRegex.findAllMatchIn(label).toList
    val activeParamRangeOpt = activeParameter.flatMap { idx =>
      paramMatches.lift(idx).map(m => (m.start, m.end))
    }

    val captureMap = captures.map { c =>
      val start = c.node.startIndex.toInt
      val end   = c.node.endIndex.toInt
      val cls   = c.name.replace(".", "-")
      (start, end) -> cls
    }.toMap

    def walkLeaves(node: SyntaxNode, acc: List[HighlightNode]): List[HighlightNode] = {
      val start    = node.startIndex.toInt
      val end      = node.endIndex.toInt
      val cls      = captureMap.get((start, end))
      val children = node.namedChildren.toSeq
      if (children.isEmpty) acc :+ HighlightNode(start, end, cls)
      else children.foldLeft(acc)((a, c) => walkLeaves(c, a))
    }

    val nodes = walkLeaves(tree.rootNode, Nil)

    def wrap(text: String, start: Int, end: Int, fallbackClass: Option[String] = None): String = {
      val overlapsActiveParam = activeParamRangeOpt.exists { case (apStart, apEnd) => start < apEnd && end > apStart }
      if (overlapsActiveParam) {
        s"<span class='active-parameter'>$text</span>"
      } else {
        fallbackClass match {
          case Some(cls) => s"<span class='$cls'>$text</span>"
          case None      => text
        }
      }
    }

    val result = nodes.foldLeft((List.empty[String], 0)) { case ((acc, last), node) =>
      val before =
        if (node.start > last) {
          val beforeText = label.substring(last, node.start)
          wrap(beforeText, last, node.start)
        } else ""

      val frag        = label.substring(node.start, node.end)
      val highlighted = wrap(frag, node.start, node.end, node.cssClass)
      (acc :+ before :+ highlighted, node.end)
    }._1

    result.mkString("")
  }

}
