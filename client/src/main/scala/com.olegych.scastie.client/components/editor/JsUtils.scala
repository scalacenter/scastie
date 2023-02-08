package com.olegych.scastie.client.components.editor

import com.olegych.scastie.api
import typings.codemirrorLint.codemirrorLintStrings

import scalajs.js.|

object JsUtils {
  type jsSeverity = codemirrorLintStrings.error | codemirrorLintStrings.info | codemirrorLintStrings.warning

  def parseSeverity(severity: api.Severity): jsSeverity = severity match {
    case api.Error   => codemirrorLintStrings.error
    case api.Info    => codemirrorLintStrings.info
    case api.Warning => codemirrorLintStrings.warning
  }

}
