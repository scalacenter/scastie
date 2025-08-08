package com.olegych.scastie.client.i18n

import scala.scalajs.js
import scala.scalajs.js.annotation._
import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

@js.native
@JSImport("pofile", JSImport.Namespace)
object POFile extends js.Object {
  def parse(str: String, callback: js.Function2[js.Any, PO, Unit]): Unit = js.native
}

@js.native
trait PO extends js.Object {
  def items: js.Array[POItem] = js.native
}

@js.native
trait POItem extends js.Object {
  def msgid: String = js.native
  def msgstr: js.Array[String] = js.native
}

object I18n {
    private var translationsByLang = Map.empty[String, Map[String, String]]
    private var currentLang: String = "en"

    private def simpleParsePo(poContent: String): Map[String, String] = {
        val regex = """msgid\s+"(.*?)"\s+msgstr\s+"(.*?)"""".r
        regex.findAllMatchIn(poContent).map(m => m.group(1) -> m.group(2)).toMap
    }

    def loadPo(lang: String, poContent: String): Unit = {
        val map = simpleParsePo(poContent)
        translationsByLang += lang -> map
    }

    def setLanguage(lang: String): Unit = {
        Languages.available.get(lang) match {
            case Some(poContent) =>
                if (!translationsByLang.contains(lang)) {
                    loadPo(lang, poContent)
                }
                currentLang = lang
            case None =>
                currentLang = "en"
        }
    }

    def t(msgid: String): String = {
        val trans = translationsByLang.get(currentLang).flatMap(_.get(msgid)).getOrElse(msgid)
        trans
    }
}