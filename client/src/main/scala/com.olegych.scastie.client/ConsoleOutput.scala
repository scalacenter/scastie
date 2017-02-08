package com.olegych.scastie
package client

import japgolly.scalajs.react._, vdom.all._

object ConsoleOutput {
  private val component = ReactComponentB[Vector[String]]("Console Output")
    .render_P(console => ul(`class` := "output")(console.map(o => li(o))))
    .build

  def apply(console: Vector[String]) = component(console)
}
