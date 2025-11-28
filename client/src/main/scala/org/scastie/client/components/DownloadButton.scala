package org.scastie.client
package components

import org.scastie.api._
import japgolly.scalajs.react._
import vdom.all._
import org.scalajs.dom
import scala.scalajs.js
import scala.collection.concurrent.TrieMap
import org.scastie.client.i18n.I18n

final case class DownloadButton(snippetId: SnippetId, scalaTarget: ScalaTarget, code: String, language: String) {
  @inline def render: VdomElement = DownloadButton.component(this)
}

object DownloadButton {
  implicit val reusability: Reusability[DownloadButton] =
    Reusability.derive[DownloadButton]

  private object BlobUrlCache {
    private val cache = TrieMap.empty[String, String]

    def getOrCreate(code: String): String =
      cache.getOrElseUpdate(code, {
        val parts = js.Array(code.asInstanceOf[dom.BlobPart])
        val blob = new dom.Blob(parts.asInstanceOf[js.Iterable[dom.BlobPart]])
        dom.URL.createObjectURL(blob)
      })

    def revokeAll(): Unit = {
      cache.valuesIterator.foreach(dom.URL.revokeObjectURL)
      cache.clear()
    }
  }

  private def safeFilenameFromSnippetId(snippetId: SnippetId): String = {
    val base = Option(snippetId.toString).filter(_.nonEmpty).getOrElse("scastie-snippet")
    base.replaceAll("/", "-")
  }

  def render(props: DownloadButton): VdomElement = {
    val isScalaCliTarget = props.scalaTarget.targetType == ScalaTargetType.ScalaCli
    val filenameBase = safeFilenameFromSnippetId(props.snippetId)
    val downloadFilename = s"$filenameBase.scala.zip"
    val url = props.snippetId.url
    val fullUrl = s"/api/download/$url"
    val hrefAttr = if (isScalaCliTarget) "#" else fullUrl

    def onClickHandler(e: ReactMouseEvent): Callback =
      if (isScalaCliTarget) {
        e.preventDefaultCB >> Callback {
          BlobUrlCache.revokeAll()
          val blobUrl = BlobUrlCache.getOrCreate(props.code)
          val a = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
          a.href = blobUrl
          a.download = s"$filenameBase.scala"
          dom.document.body.appendChild(a)
          a.click()
          a.remove()
        }
      } else Callback.empty

    li(
      a(
        href := hrefAttr,
        download := downloadFilename,
        title := I18n.t("editor.download"),
        role := "button",
        cls := "btn",
        onClick ==> (e => onClickHandler(e))
      )(I18n.t("editor.download"))
    )
  }

  private val component =
    ScalaComponent
      .builder[DownloadButton]("DownloadButton")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}

