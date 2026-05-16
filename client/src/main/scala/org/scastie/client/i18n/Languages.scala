package org.scastie.client.i18n

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@JSImport("@resources/locales/en.po?raw", JSImport.Default)
@js.native
object EnPo extends js.Any

@JSImport("@resources/locales/ja.po?raw", JSImport.Default)
@js.native
object JaPo extends js.Any

@JSImport("@resources/locales/br.po?raw", JSImport.Default)
@js.native
object BrPo extends js.Any

object Languages {

  val available: Map[String, String] = Map(
    "en" -> (EnPo.asInstanceOf[String]),
    "ja" -> (JaPo.asInstanceOf[String]),
	  "br" -> (BrPo.asInstanceOf[String])
  )

}
