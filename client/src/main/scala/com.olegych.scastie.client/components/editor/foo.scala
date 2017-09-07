package com.olegych.scastie.client
package components

import com.olegych.scastie.api

import codemirror.{
  CodeMirror,
  Hint,
  HintConfig,
  LineWidget,
  TextAreaEditor,
  TextMarker,
  TextMarkerOptions,
  Editor => CodeMirrorEditor2,
}

import codemirror.CodeMirror.{Pos => CMPosition}

import japgolly.scalajs.react._, vdom.all._, extra._

import extra.{Reusability, StateSnapshot}
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.{
  HTMLDivElement,
  HTMLElement,
  HTMLPreElement,
  HTMLTextAreaElement
}
import org.scalajs.dom.console

import scala.scalajs._