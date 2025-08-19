package com.olegych.scastie.client.i18n

import scala.scalajs.js
import scala.scalajs.js.annotation._
import org.scalajs.dom
import org.scalajs.dom.HTMLElement
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

    private def parsePo(poContent: String): Map[String, String] = {
        val msgidRegex = """^msgid\s+"(.*)"""".r
        val msgstrRegex = """^msgstr\s+"(.*)"""".r
        val quotedLine = """^"(.*)"""".r

        var msgid: Option[String] = None
        var msgstr: Option[String] = None
        var collectingMsgid = false
        var collectingMsgstr = false
        var msgidBuffer = new StringBuilder
        var msgstrBuffer = new StringBuilder
        val translations = scala.collection.mutable.Map.empty[String, String]

        for (line <- poContent.linesIterator) {
            line.trim match {
            case msgidRegex(first) =>
                collectingMsgid = true
                collectingMsgstr = false
                msgidBuffer.clear()
                msgidBuffer.append(first)
            case msgstrRegex(first) =>
                collectingMsgid = false
                collectingMsgstr = true
                msgstrBuffer.clear()
                msgstrBuffer.append(first)
            case quotedLine(text) if collectingMsgid =>
                msgidBuffer.append(text)
            case quotedLine(text) if collectingMsgstr =>
                msgstrBuffer.append(text)
            case l if l.isEmpty && msgidBuffer.nonEmpty && msgstrBuffer.nonEmpty =>
                translations += msgidBuffer.toString -> msgstrBuffer.toString
                msgidBuffer.clear()
                msgstrBuffer.clear()
                collectingMsgid = false
                collectingMsgstr = false
            case _ =>
            }
        }
        if (msgidBuffer.nonEmpty && msgstrBuffer.nonEmpty) {
            translations += msgidBuffer.toString -> msgstrBuffer.toString
        }
        translations.toMap
    }

    def loadPo(lang: String, poContent: String): Unit = {
        val map = parsePo(poContent)
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

    def getLanguage: String = currentLang

    def t(msgid: String): String = {
        val trans = translationsByLang.get(currentLang).flatMap(_.get(msgid)).getOrElse(msgid)
        trans
    }
}