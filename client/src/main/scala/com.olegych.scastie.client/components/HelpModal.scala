package com.olegych.scastie.client.components

import japgolly.scalajs.react._
import vdom.all._
import com.olegych.scastie.client.components.editor.EditorKeymaps

import com.olegych.scastie.client.i18n.I18n
import japgolly.scalajs.react.hooks.HookCtx.I1

final case class HelpModal(isDarkTheme: Boolean, isClosed: Boolean, close: Reusable[Callback]) {
  @inline def render: VdomElement = HelpModal.component(this)
}

object HelpModal {
  implicit val reusability: Reusability[HelpModal] =
    Reusability.derive[HelpModal]

  private def render(props: HelpModal): VdomElement = {
    def generateATag(url: String, text: String) =
      a(href := url, target := "_blank", rel := "nofollow", text)

    val scastieGithub =
      generateATag("https://github.com/scalacenter/scastie", "scalacenter/scastie")

    val sublime = generateATag(
      "https://sublime-text-unofficial-documentation.readthedocs.org/en/latest/reference/keyboard_shortcuts_osx.html",
      I18n.t("keyboard shortcuts.")
    )

    val scalafmtConfiguration =
      generateATag("https://scalameta.org/scalafmt/docs/configuration.html#disabling-or-customizing-formatting", "configuration section")

    val originalScastie =
      generateATag("https://github.com/OlegYch/scastie_old", "GitHub")

    val gitter =
      generateATag("https://gitter.im/scalacenter/scastie", "Gitter")

    Modal(
      title = I18n.t("Help about Scastie"),
      isDarkTheme = props.isDarkTheme,
      isClosed = props.isClosed,
      close = props.close,
      modalCss = TagMod(),
      modalId = "long-help",
      content = div(cls := "markdown-body")(
        p( I18n.t("Scastie is an interactive playground for Scala with support for sbt configuration.")),
        p( s"${I18n.t("Scastie editor supports Sublime Text")} $sublime"),
        h2(I18n.t("Editor Modes")),
        p(
          I18n.t("Scastie editor supports multiple keyboard modes. "),
          I18n.t("You can switch between Default, Vim, and Emacs modes using the selector in the sidebar. "),
          I18n.t("Each mode provides familiar keybindings and navigation for users of those editors.")
        ),
        h2(s"${I18n.t("Save")} (${EditorKeymaps.saveOrUpdate.getName})"),
        p(
          I18n.t("Run and save your code.")
        ),
        h2(s"${I18n.t("New")} (${EditorKeymaps.openNewSnippetModal.getName})"),
        p(
          I18n.t("Removes all your code lines from the current editor instance and resets sbt configuration.")
        ),
        h2(s"${I18n.t("Clear Messages")} (${EditorKeymaps.clear.getName})"),
        p(
          I18n.t("Removes all messages from the current editor instance.")
        ),
        h2(s"${I18n.t("Format")} (${EditorKeymaps.format.getName})"),
        p(
          s"${I18n.t("The code formatting is done by scalafmt. You can configure the formatting with comments in your code. Read the")} $scalafmtConfiguration"),
        h2(I18n.t("Worksheet")),
        p(
          I18n.t("Enabled by default, the Worksheet Mode gives the value and the type of each line of your program. You can also add HTML blocks such as: "),
          code(
            "html\"<h1>Hello</h1>\""
          ),
          I18n.t(" to render it next to the declaration.")
        ),
        p(
          I18n.t("In Worksheet Mode you cannot use packages. If you want to use it, turn off the Mode and add a main method and println statements.")
        ),
        h2(I18n.t("Download")),
        p(
          I18n.t("Create a zip package with sbt configuration for current snippet.")
        ),
        h2(I18n.t("Embed")),
        p(
          I18n.t("Create an url embeddable in external web pages.")
        ),
        h2(s"${I18n.t("Console")} (${EditorKeymaps.console.getName})"),
        p(
          I18n.t("You can see your code's output in the Scastie's console.")
        ),
        h2(I18n.t("Build Settings")),
        p(
          I18n.t("In Build Settings you can change the Scala version and add libraries, choose your desired target and even add your own custom sbt configuration.")
        ),
        h2(I18n.t("User's Code Snippets")),
        p(
          I18n.t("Your saved code fragments will appear here and you'll be able to delete or share them.")
        ),
        h2(I18n.t("Vim commands")),
        p(
          I18n.t("If Vim mode is enabled in the editor, you can use the following commands in the Vim command bar (press ':' in normal mode):")
        ),
        ul(
          li(code(":w"), " / ", code(":run"), I18n.t(" — Run and save your code")),
          li(code(":f"), " / ", code(":format"), I18n.t(" — Format code")),
          li(code(":c"), " / ", code(":clear"), I18n.t(" — Clear messages")),
          li(code(":h"), " / ", code(":help"), I18n.t(" — Show this help dialog"))
        ),
        p(
          "You can also use standard Vim navigation and editing commands in the editor."
        ),
        h2(I18n.t("Feedback")),
        p( I18n.t("You can join our "), gitter, I18n.t(" channel and send issues.")),
        h2(I18n.t("BuildInfo")),
        p( I18n.t("It's available on Github at "))(
          scastieGithub,
          br,
          s" ${I18n.t("License")}: Apache 2",
        ),
        p(

          I18n.t("Scastie is an original idea from Aleh Aleshka (OlegYch) ")
        )(
          originalScastie
        )
      )
    ).render
  }

  private val component =
    ScalaFnComponent
      .withHooks[HelpModal]
      .renderWithReuse(render)
}
