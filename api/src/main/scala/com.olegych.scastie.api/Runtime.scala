package com.olegych.scastie
package api

import buildinfo.BuildInfo.{version => buildVersion, githash}

package object runtime {

  val Html = api.Html

  implicit class HtmlHelper(val sc: StringContext) extends AnyVal {
    def html(args: Any*) = Html(sc.s(args: _*))
    def htmlRaw(args: Any*) = Html(sc.raw(args: _*))
  }

  val help = {
    // A stylesheets select the correct one to display
    val Ctrl = "<kbd class='pc'>Ctrl&nbsp;&nbsp;</kbd>"
    val Cmd =
      """<kbd class='mac'><span class="oi" data-glyph="command"></span></kbd>"""

    val CC = Ctrl + Cmd

    val Enter =
      "<kbd>&nbsp;&nbsp;Enter&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</kbd>"

    def kbd(label: String): String = s"<kbd>&nbsp;$label&nbsp;</kbd>"
    val F2 = kbd("F2")
    val F3 = kbd("F3")
    val F4 = kbd("F4")
    val F6 = kbd("F6")
    val Esc = kbd("Esc")
    val S = kbd("S")

    def a(url: String, text: String) =
      s"""<a target="_blank" href="$url" rel="nofollow">$text</a>"""

    val scastieGithub =
      a("https://github.com/scalacenter/scastie", "scalacenter/scastie")

    val sublime = a(
      "https://sublime-text-unofficial-documentation.readthedocs.org/en/latest/reference/keyboard_shortcuts_osx.html",
      "Sublime Text Keyboard Shortcuts are also supported"
    )

    val scalafmtConfiguration = a(
      "https://olafurpg.github.io/scalafmt/#Configuration",
      "configuration section"
    )

    val sbt = a("http://www.scala-sbt.org/0.13/docs/index.html", "sbt")

    val originalScastie = a(
      "https://github.com/OlegYch/scastie",
      "GitHub"
    )

    html"""|<h1>Welcome to Scastie!</h1>
           |Scastie is an interactive playground for Scala. To not see this 
           |message again, remove the <code>help</code> expression on your code snippet.
           |
           |<h2>Libraries</h2>
           |In Libraries you can change the Scala version and add libaries.
           |
           |<h2>Worksheet Mode</h2>
           |Enabled by default, the Worksheet Mode gives the value and the type of each line of
           |your program. You can also add HTML blocks such as: <code>html"&lt;h1&gt;Hello&lt;/h1&gt;".fold</code> to
           |break down your program into various sections.
           |
           |In Worksheet Mode you cannot use <i>package</i> or <i>value classes</i>. If you want to use
           |those features, turn off the Mode and add a <i>main</i> method and <i>println</i> statements.
           |
           |<h2>Formatting Code</h2>
           |The code formatting is done by scalafmt. You can configure the formatting with comments
           |in your code. Read the $scalafmtConfiguration
           |
           |<h2>Keyboard Shortcuts</h2>
           |<pre>
           |Editor View
           |  Run                                       $CC + $Enter
           |  Clear annotations, Close console output   $Esc
           |  Save                                      $CC + $S
           |  Format Code                               $F6
           |  Toogle Console                            $F3
           |  Toggle Theme (Solarized Light/Dark)       $F2
           |  Toogle Worksheet Mode                     $F4
           |</pre>
           |
           |$sublime
           |
           |<h2>BuildInfo</h2>
           |It's available on Github at $scastieGithub
           |License: Apache 2
           |Version: $buildVersion
           |Git: $githash
           |
           |Scastie is an original idea from Aleh Aleshka (OlegYch) $originalScastie""".stripMargin.fold
  }
}
