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

    val Dot = "<kbd>&nbsp;&nbsp;.&nbsp;&nbsp;</kbd>"
    val Space = s"""<kbd>${"&nbsp;" * 40}</kbd>"""

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
    val F7 = kbd("F7")
    val Esc = kbd("Esc")

    val key1 = kbd("1")
    val key2 = kbd("2")

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

    html"""|<h1>Welcome to Scastie!</h1>
           |Scastie is an interractive playground. It can run anything sbt can run.
           |
           |<h2>Keyboard Shortcuts</h2>
           |<pre>
           |Editor View $CC + $key1
           |  Run                                       $CC + $Enter
           |  Save                                      $CC + S
           |  Clear annotations, Close console output   $Esc
           |  Toggle Theme (Solarized Light/Dark)       $F2
           |  Toogle console                            $F3
           |  Toogle Script Mode                        $F4
           |
           |Settings View $CC + $key2
           |
           |$sublime
           |</pre>
           |
           |<h2>Settings</h2>
           |Targets
           |Scala Version
           |Scala Libraries
           |Sbt
           |It's possible to have additional sbt configuration such as scalacOptions.
           |
           |<h2>Script Mode</h2>
           |The Script Mode will print the value and the type of each line.
           |By default the script mode is active. Since you cannot add top-level code
           |such as value classes (AnyVal) or package/package object it's possible to disable it.
           |
           |<h2>Formatting Code</h2>
           |The code formatting is done by scalafmt. You can configure the formatting with comments
           |in your code. Read the $scalafmtConfiguration
           |
           |$scastieGithub
           |published under the Apache2 license
           |Version: $buildVersion
           |GitHash: $githash""".stripMargin.fold
  }
}
