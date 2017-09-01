package com.olegych.scastie.client.components

import com.olegych.scastie.buildinfo.BuildInfo.{gitHash, version}

import japgolly.scalajs.react._, vdom.all._, extra._

final case class HelpModal(isClosed: Boolean, close: Reusable[Callback]) {
  @inline def render: VdomElement = HelpModal.component(this)
}

object HelpModal {
  implicit val reusability: Reusability[HelpModal] =
    Reusability.caseClass[HelpModal]

  private def render(props: HelpModal): VdomElement = {
    def generateATag(url: String, text: String) =
      a(href := url, target := "_blank", rel := "nofollow", text)

    val scastieGithub =
      generateATag("https://github.com/scalacenter/scastie",
                   "scalacenter/scastie")

    val sublime = generateATag(
      "https://sublime-text-unofficial-documentation.readthedocs.org/en/latest/reference/keyboard_shortcuts_osx.html",
      "Sublime Text Keyboard Shortcuts are also supported"
    )

    val scalafmtConfiguration =
      generateATag("https://olafurpg.github.io/scalafmt/#Configuration",
                   "configuration section")

    val originalScastie =
      generateATag("https://github.com/OlegYch/scastie", "GitHub")

    Modal(
      "Help about Scastie",
      props.isClosed,
      props.close,
      TagMod(),
      TagMod(
        p(cls := "normal", "Scastie is an interactive playground for Scala."),
        h2("Run / Edit"),
        p(cls := "normal",
          "The code editor where you can write and run your code."),
        div(cls := "indent")(
          h3("Clear"),
          p(
            cls := "normal",
            "Removes all your code lines from the current editor instance."
          ),
          h3("Format"),
          p(cls := "normal",
            "The code formatting is done by scalafmt. You can configure the formatting with comments in your code. Read the ",
            scalafmtConfiguration),
          h3("Worksheet"),
          p(
            cls := "normal",
            """Enabled by default, the Worksheet Mode gives the value and the type of each line of your program. You can also add HTML blocks such as: html "<h1>Hello</h1>".fold to break down your program into various sections."""
          ),
          p(
            cls := "normal",
            "In Worksheet Mode you cannot use package or value classes. If you want to use those features, turn off the Mode and add a main method and println statements."
          ),
          h3("Save"),
          p(
            cls := "normal",
            "You can save your code fragments just clicking this button. (You need to be logged in with your Github account for this to work) Once they’re saved, you’ll be able to Update, Fork or Share them."
          )
        ),
        h2("Build Settings"),
        p(
          cls := "normal",
          "In Build Settings you can change the Scala version and add libraries, choose your desired target and even add your own custom sbt configuration."
        ),
        h2("User's Code Snippets"),
        p(
          cls := "normal",
          "Your saved code fragments will appear here and you’ll be able to edit or share them."
        ),
        h2("Console"),
        p(
          cls := "normal",
          "You can see your code’s output in the Scastie’s console."
        ),
        h2("Feedback"),
        p(cls := "normal", "You can join our Gitter channel and send issues."),
        h2("Keyboard shortcut"),
        div(cls := "shortcuts")(
          table(
            tbody(
              tr(
                th("Editor view")(
                  br,
                  "Run"
                ),
                td(
                  span("⌘"),
                  "+",
                  span("Enter")
                )
              ),
              tr(
                th("Clear annotations, Close console"),
                td(
                  span("Esc")
                )
              ),
              tr(
                th("Save"),
                td(
                  span("⌘"),
                  "+",
                  span("S")
                )
              ),
              tr(
                th("Format Code"),
                td(
                  span("F6")
                )
              ),
              tr(
                th("Toggle Console"),
                td(
                  span("F3")
                )
              ),
              tr(
                th("Toggle Theme"),
                td(
                  span("F2")
                )
              ),
              tr(
                th("Toggle Worksheet Mode"),
                td(
                  span("F4")
                )
              )
            )
          )
        ),
        p(cls := "normal")(sublime),
        h2("BuildInfo"),
        p(cls := "normal", "It's available on Github at ")(
          scastieGithub,
          br,
          " License: Apache 2",
          br,
          s"Version: $version",
          br,
          s"Git: $gitHash"
        ),
        p(
          cls := "normal",
          "Scastie is an original idea from Aleh Aleshka (OlegYch) "
        )(
          originalScastie
        )
      )
    ).render
  }

  private val component =
    ScalaComponent
      .builder[HelpModal]("HelpModal")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
