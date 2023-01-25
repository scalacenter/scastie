package com.olegych.scastie.client.components.editor

import com.olegych.scastie.api
import scalajs.js.|
import typings.codemirrorLint.codemirrorLintStrings

object JsUtils {
  type jsSeverity = codemirrorLintStrings.error | codemirrorLintStrings.info | codemirrorLintStrings.warning

  def parseSeverity(severity: api.Severity): jsSeverity = severity match {
    case api.Error   => codemirrorLintStrings.error
    case api.Info    => codemirrorLintStrings.info
    case api.Warning => codemirrorLintStrings.warning
  }

}
