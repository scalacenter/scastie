package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._
import vdom.all._

final case class ReloadStaleMetals(
  reload: Reusable[Callback]
) {
  @inline def render: VdomElement = ReloadStaleMetals.component(this)
}

object ReloadStaleMetals {
  private def render(props: ReloadStaleMetals): VdomElement = {
    li(
      role := "button",
      title := "Reload metals",
      cls := "btn editor reload-metals-btn",
      onClick --> props.reload
    )(
      i(cls := "fa fa-refresh"),
      span("Reload metals")
    )
  }

  private val component =
    ScalaFnComponent
      .withHooks[ReloadStaleMetals]
      .render(props => ReloadStaleMetals.render(props))
}