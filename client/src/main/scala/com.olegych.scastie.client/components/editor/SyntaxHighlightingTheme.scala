package com.olegych.scastie.client.components.editor

import typings.codemirrorLanguage.mod
import typings.lezerHighlight.mod.tags

import scalajs.js

object SyntaxHighlightingTheme {

  private val highlightStyle = mod.HighlightStyle.define(
    js.Array(
      mod.TagStyle(tags.annotation).setClass("attribute"),
      mod.TagStyle(tags.arithmeticOperator).setClass("operator"),
      mod.TagStyle(tags.attributeName).setClass("tag-attribute"),
      mod.TagStyle(tags.attributeValue).setClass("string"),
      mod.TagStyle(tags.bitwiseOperator).setClass("operator"),
      mod.TagStyle(tags.blockComment).setClass("comment"),
      mod.TagStyle(tags.bool).setClass("boolean"),
      mod.TagStyle(tags.brace).setClass("punctuation-bracket"),
      mod.TagStyle(tags.bracket).setClass("punctuation-bracket"),
      mod.TagStyle(tags.character).setClass("character"),
      mod.TagStyle(tags.className).setClass("type"),
      mod.TagStyle(tags.comment).setClass("comment"),
      mod.TagStyle(tags.compareOperator).setClass("operator"),
      mod.TagStyle(tags.content).setClass("text"),
      mod.TagStyle(tags.contentSeparator).setClass("punctuation-delimiter"),
      mod.TagStyle(tags.controlKeyword).setClass("keyword"),
      mod.TagStyle(tags.controlOperator).setClass("operator"),
      mod.TagStyle(tags.definitionKeyword).setClass("keyword"),
      mod.TagStyle(tags.definitionOperator).setClass("keyword-operator"),
      mod.TagStyle(tags.derefOperator).setClass("operator"),
      mod.TagStyle(tags.docComment).setClass("comment"),
      mod.TagStyle(tags.docString).setClass("string"),
      mod.TagStyle(tags.emphasis).setClass("text-emphasis"),
      mod.TagStyle(tags.escape).setClass("string-escape"),
      mod.TagStyle(tags.float).setClass("float"),
      mod.TagStyle(tags.integer).setClass("number"),
      mod.TagStyle(tags.invalid).setClass("error"),
      mod.TagStyle(tags.keyword).setClass("keyword"),
      mod.TagStyle(tags.labelName).setClass("label"),
      mod.TagStyle(tags.lineComment).setClass("comment"),
      mod.TagStyle(tags.literal).setClass("text-literal"),
      mod.TagStyle(tags.logicOperator).setClass("operator"),
      mod.TagStyle(tags.macroName).setClass("function-macro"),
      mod.TagStyle(tags.modifier).setClass("keyword"),
      mod.TagStyle(tags.moduleKeyword).setClass("keyword"),
      mod.TagStyle(tags.namespace).setClass("namespace"),
      mod.TagStyle(tags.number).setClass("number"),
      mod.TagStyle(tags.operator).setClass("operator"),
      mod.TagStyle(tags.operatorKeyword).setClass("keyword-operator"),
      mod.TagStyle(tags.paren).setClass("punctuation-bracket"),
      mod.TagStyle(tags.propertyName).setClass("property"),
      mod.TagStyle(tags.punctuation).setClass("punctuation-delimiter"),
      mod.TagStyle(tags.quote).setClass("punctuation-special"),
      mod.TagStyle(tags.regexp).setClass("string-regex"),
      mod.TagStyle(tags.self).setClass("variablee"),
      mod.TagStyle(tags.separator).setClass("punctuation-delimiter"),
      mod.TagStyle(tags.squareBracket).setClass("punctuation-bracket"),
      mod.TagStyle(tags.strikethrough).setClass("text-strike"),
      mod.TagStyle(tags.string).setClass("string"),
      mod.TagStyle(tags.strong).setClass("text-strong"),
      mod.TagStyle(tags.tagName).setClass("tag"),
      mod.TagStyle(tags.typeName).setClass("type"),
      mod.TagStyle(tags.typeOperator).setClass("type-qualifier"),
      mod.TagStyle(tags.unit).setClass("none"),
      mod.TagStyle(tags.variableName).setClass("function"),
    )
  )

  val highlightingTheme = mod.syntaxHighlighting(highlightStyle)

}
