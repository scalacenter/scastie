package api

package object runtime {
  implicit class HtmlHelper(val sc: StringContext) extends AnyVal {
    def html(args: Any*) = Html(sc.s(args: _*))
    def htmlRaw(args: Any*) = Html(sc.raw(args: _*))
  }

  val help = {

    val Dot = "<kbd>&nbsp;&nbsp;.&nbsp;&nbsp;</kbd>"
    val Space = s"""<kbd>${"&nbsp;" * 40}</kbd>"""
    val Ctrl = "<kbd class='pc'>Ctrl&nbsp;&nbsp;</kbd>"
    val Cmd = "<kbd class='mac'>&nbsp;⌘&nbsp;</kbd>"
    val Enter = "<kbd>&nbsp;&nbsp;Enter&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</kbd>"
    val F2 = "<kbd>&nbsp;F2&nbsp;</kbd>"
    val F7 = "<kbd>&nbsp;F7&nbsp;</kbd>"
    val Esc = "<kbd>&nbsp;Esc&nbsp;</kbd>"
    val github = "https://github.com/scalacenter/scastie"
    val sublime = "http://sublime-text-unofficial-documentation.readthedocs.org/en/latest/reference/keyboard_shortcuts_osx.html"

    html"""|<h1>Welcome to Scastie!</h1>
           |Scastie is an interractive playground.
           |Evaluate expressions with $Ctrl $Cmd + $Enter.
           |Clear the output with $Esc.
           |<pre>
           |clear           $Esc
           |run             $Ctrl $Cmd + $Enter
           |toggle theme    $F2
           |<a target="_blank" href="$sublime">Sublime Text Keyboard Shortcuts</a>
           |</pre>
           |The source code is available at <a target="_blank" href="$github">scalacenter/scastie</a>,
           |published under the Apache2 license
           |""".stripMargin.fold
  }
}
