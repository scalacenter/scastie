package org.scastie.client.components

import japgolly.scalajs.react._
import vdom.all._

final case class ChangelogModal(isDarkTheme: Boolean, isClosed: Boolean, close: Reusable[Callback]) {
  @inline def render: VdomElement = ChangelogModal.component(this)
}

object ChangelogModal {
  implicit val reusability: Reusability[ChangelogModal] =
    Reusability.derive[ChangelogModal]

  val currentVersion: String = "2026-02-18"

  private def render(props: ChangelogModal): VdomElement = {
    def prLink(number: Int) =
      a(href := s"https://github.com/scalacenter/scastie/pull/$number", target := "_blank", rel := "nofollow", s"#$number")

    def ghUser(username: String) =
      a(href := s"https://github.com/$username", target := "_blank", rel := "nofollow", cls := "changelog-contributor", s"@$username")

    Modal(
      title = "What's new in Scastie",
      isDarkTheme = props.isDarkTheme,
      isClosed = props.isClosed,
      close = props.close,
      modalCss = TagMod(),
      modalId = "changelog",
      content = div(cls := "changelog-content markdown-body")(
        /* Highlighted features */
        div(cls := "changelog-feature")(
          h3("Japanese translation (", prLink(1243), ")"),
          p(
            "Scastie now supports Japanese as a UI language. ",
            "Thanks to ", ghUser("windymelt"), " for this contribution!"
          )
        ),

        div(cls := "changelog-feature")(
          h3("Actionable diagnostics (", prLink(1238), ")"),
          p(
            "Scastie can now suggest quick fixes for compiler diagnostics. ",
            "When a diagnostic has an associated code action, you'll see it directly in the editor."
          ),
          img(src := "https://github.com/user-attachments/assets/d9a896eb-123c-45d9-be5a-5795245c62ff", alt := "Actionable diagnostics demo")
        ),

        div(cls := "changelog-feature")(
          h3("Better build error reporting (", prLink(1235), ")"),
          p(
            "All BSP log messages are now captured and forwarded, ",
            "giving you more context to diagnose configuration issues. ",
            "This also enables using compiler flags like ", code("-Vprint"), " to inspect intermediate compilation phases."
          ),
          img(src := "https://github.com/user-attachments/assets/79eb7491-715e-475c-8358-e12f746221c0", alt := "Vprint flag demo")
        ),

        /* Bug fixes */
        hr(),
        h3("Bug Fixes"),
        ul(
          li("Fix signature help spam by caching active parameter (", prLink(1220), ")"),
          li("Fix libraries not loading in Build Settings (", prLink(1240), ")"),
          li("Fix download button (", prLink(1231), ")"),
          li("Fix nightly version resolution (", prLink(1245), ")"),
          li("Prevent duplicate snippet URLs when saving without changes (", prLink(1242), ")"),
          li("Prevent stale diagnostics after version change (", prLink(1247), ")"),
          li("Fix language in embed mode (", prLink(1233), ")")
        ),

        h3("Improvements"),
        ul(
          li("Per-user presentation compiler caching (", prLink(1224), ")"),
          li("Consistent warning display with compilation info cache (", prLink(1169), ")")
        ),

        h3("Version Bumps"),
        ul(
          li("Scala 3.8.2-RC3 (", prLink(1253), ")")
        )
      )
    ).render
  }

  private val component =
    ScalaFnComponent
      .withHooks[ChangelogModal]
      .renderWithReuse(render)
}
