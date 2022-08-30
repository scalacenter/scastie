package com.olegych.scastie.client.components.editor

import typings.codemirrorLanguage.mod
import typings.lezerHighlight.mod.tags

import scalajs.js

object SyntaxHighlightingTheme {

  private val highlightStyle = mod.HighlightStyle.define(
    js.Array(
      mod.TagStyle(tags.keyword).setClass("cm-keyword"),
      mod.TagStyle(tags.atom).setClass("cm-atom"),
      mod.TagStyle(tags.definitionKeyword).setClass("cm-def"),
      mod.TagStyle(tags.variableName).setClass("cm-variable"),
      mod.TagStyle(tags.propertyName).setClass("cm-property"),
      mod.TagStyle(tags.operator).setClass("cm-operator"),
      mod.TagStyle(tags.comment).setClass("cm-comment"),
      mod.TagStyle(tags.string).setClass("cm-string"),
      mod.TagStyle(tags.meta).setClass("cm-meta"),
      mod.TagStyle(tags.invalid).setClass("cm-invalidchar"),
    )
  )

  val highlightingTheme = mod.syntaxHighlighting(highlightStyle)

}
