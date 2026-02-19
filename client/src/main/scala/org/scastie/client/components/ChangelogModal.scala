package org.scastie.client.components

import japgolly.scalajs.react._
import scalajs.js
import vdom.all._
import scala.scalajs.js.annotation.JSImport
import org.scastie.buildinfo.BuildInfo

final case class ChangelogModal(isDarkTheme: Boolean, isClosed: Boolean, close: Reusable[Callback]) {
  @inline def render: VdomElement = ChangelogModal.component(this)
}

object ChangelogModal {
  implicit val reusability: Reusability[ChangelogModal] =
    Reusability.derive[ChangelogModal]

  @js.native
  @JSImport("@scastieRoot/changelog.md", "html")
  val changelogHTMLContent: String = js.native

  val currentVersion: String = BuildInfo.versionBase

  private def render(props: ChangelogModal): VdomElement = {
    Modal(
      title = s"What's new in Scastie ($currentVersion)",
      isDarkTheme = props.isDarkTheme,
      isClosed = props.isClosed,
      close = props.close,
      modalCss = TagMod(),
      modalId = "changelog",
      content = div(cls := "changelog-content markdown-body", dangerouslySetInnerHtml := changelogHTMLContent)
    ).render
  }

  private val component =
    ScalaFnComponent
      .withHooks[ChangelogModal]
      .renderWithReuse(render)
}
