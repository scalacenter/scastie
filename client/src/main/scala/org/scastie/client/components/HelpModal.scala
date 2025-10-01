package org.scastie.client.components

import japgolly.scalajs.react._
import japgolly.scalajs.react.hooks.HookCtx.I1
import org.scastie.client.components.editor.EditorKeymaps
import org.scastie.client.i18n.I18n
import vdom.all._

final case class HelpModal(isDarkTheme: Boolean, isClosed: Boolean, close: Reusable[Callback]) {
  @inline def render: VdomElement = HelpModal.component(this)
}

object HelpModal {
  implicit val reusability: Reusability[HelpModal] = Reusability.derive[HelpModal]

  private def render(props: HelpModal): VdomElement = {
    def generateATag(url: String, text: String) = a(href := url, target := "_blank", rel := "nofollow", text)

    def renderWithElement(template: String, elementBuilder: String => VdomElement): VdomElement = {
      val elementRegex = """\{([^}]+)\}""".r

      elementRegex.findFirstMatchIn(template) match {
        case Some(m) =>
          val before = template.substring(0, m.start)
          val elementContent = m.group(1)
          val element = elementBuilder(elementContent)
          val after = template.substring(m.end)

          p(before, element, after)
        case None => p(template)
      }
    }

    val originalScastie = generateATag("https://github.com/OlegYch/scastie_old", "GitHub")

    Modal(
      title = I18n.t("help.title"),
      isDarkTheme = props.isDarkTheme,
      isClosed = props.isClosed,
      close = props.close,
      modalCss = TagMod(),
      modalId = "long-help",
      content = div(cls := "markdown-body")(
        p(I18n.t("help.description")),
        p(
          renderWithElement(
            I18n.t("help.sublime_support"),
            content =>
              generateATag(
                "https://sublime-text-unofficial-documentation.readthedocs.org/en/latest/reference/keyboard_shortcuts_osx.html",
                content
              )
          )
        ),
        h2(I18n.t("help.editor_modes")),
        p(
          I18n.t("help.editor_modes_1"),
          I18n.t("help.editor_modes_2"),
          I18n.t("help.editor_modes_3")
        ),
        h2(s"${I18n.t("help.save")} (${EditorKeymaps.saveOrUpdate.getName})"),
        p(
          I18n.t("help.save_description")
        ),
        h2(s"${I18n.t("editor.new")} (${EditorKeymaps.openNewSnippetModal.getName})"),
        p(
          I18n.t("help.new_description")
        ),
        h2(s"${I18n.t("editor.clear_messages")} (${EditorKeymaps.clear.getName})"),
        p(
          I18n.t("help.clear_messages_description")
        ),
        h2(s"${I18n.t("editor.format")} (${EditorKeymaps.format.getName})"),
        p(
          renderWithElement(
            I18n.t("help.format_description"),
            content =>
              generateATag(
                "https://scalameta.org/scalafmt/docs/configuration.html#disabling-or-customizing-formatting",
                content
              )
          )
        ),
        h2(I18n.t("editor.worksheet")),
        p(
          renderWithElement(
            I18n.t("help.worksheet_description"),
            content => code(content)
          )
        ),
        p(
          I18n.t("help.worksheet_packages_warning")
        ),
        h2(I18n.t("editor.download")),
        p(
          I18n.t("help.download_description")
        ),
        h2(I18n.t("editor.embed")),
        p(
          I18n.t("help.embed_description")
        ),
        h2(s"${I18n.t("console.title")} (${EditorKeymaps.console.getName})"),
        p(
          I18n.t("help.console_description")
        ),
        h2(I18n.t("sidebar.build_settings")),
        p(
          I18n.t("help.build_settings_description")
        ),
        h2(I18n.t("help.snippets_title")),
        p(
          I18n.t("help.snippets_description")
        ),
        h2(I18n.t("help.vim_commands")),
        p(
          I18n.t("help.vim_description")
        ),
        ul(
          li(code(":w"), " / ", code(":run"), I18n.t("help.vim_run")),
          li(code(":f"), " / ", code(":format"), I18n.t("help.vim_format")),
          li(code(":c"), " / ", code(":clear"), I18n.t("help.vim_clear")),
          li(code(":h"), " / ", code(":help"), I18n.t("help.vim_help"))
        ),
        p(
          I18n.t("help.vim_navigation")
        ),
        h2(I18n.t("topbar.feedback")),
        p(
          renderWithElement(
            I18n.t("help.feedback_description"),
            content => generateATag("https://gitter.im/scalacenter/scastie", content)
          )
        ),
        h2(I18n.t("help.buildinfo")),
        p(
          renderWithElement(
            I18n.t("help.github_info"),
            content => generateATag("https://github.com/scalacenter/scastie", content)
          )
        ),
        p(
          s"${I18n.t("help.license")}: Apache 2"
        ),
        p(
          s"${I18n.t("help.original_idea")} "
        )(
          originalScastie
        )
      )
    ).render
  }

  private val component = ScalaFnComponent
    .withHooks[HelpModal]
    .renderWithReuse(render)

}
