package com.olegych.scastie.client.i18n

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@JSImport("@resources/locales/en.po?raw", JSImport.Default)
@js.native
object EnPo extends js.Any

@JSImport("@resources/locales/pl.po?raw", JSImport.Default)
@js.native
object PlPo extends js.Any

object Languages {
  val available: Map[String, String] = Map(
    "en" -> (EnPo.asInstanceOf[String]),
    "pl" -> (PlPo.asInstanceOf[String])
  )
}