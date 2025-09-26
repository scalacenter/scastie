package org.scastie.instrumentation

import org.scastie.runtime.api._

object RuntimeConstants {
  val instrumentedObject    = Instrumentation.instrumentedObject
  val instrumentationMethod = "instrumentations$"

  val emptyMapT         = "_root_.scala.collection.mutable.Map.empty"
  val jsExportT         = "_root_.scala.scalajs.js.annotation.JSExport"
  val jsExportTopLevelT = "_root_.scala.scalajs.js.annotation.JSExportTopLevel"

  val runtimePackage           = "_root_.org.scastie.runtime"
  val runtimeApiPackage        = "_root_.org.scastie.runtime.api"
  val positionT                = s"$runtimeApiPackage.Position"
  val renderT                  = s"$runtimeApiPackage.Render"
  val runtimeErrorT            = s"$runtimeApiPackage.RuntimeError"
  val instrumentationT         = s"$runtimeApiPackage.Instrumentation"
  val runtimeT                 = s"$runtimePackage.Runtime"
  val domhookT                 = s"$runtimePackage.DomHook"
  val instrumentationRecorderT = s"$runtimePackage.InstrumentationRecorder"
}
