package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._

import scala.scalajs.js.annotation.JSImport

import vdom.all._
import scalajs.js

final case class MetalsStatusIndicator(
    metalsStatus: MetalsStatus,
    toggleMetalsStatus: Reusable[Callback],
    view: View,
) {
  @inline def render: VdomElement = MetalsStatusIndicator.component(this)
}

@JSImport("@resources/images/scalameta-logo.png", JSImport.Default)
@js.native
object MetalsLogo extends js.Any


object MetalsStatusIndicator {
  def metalsLogo: String = MetalsLogo.asInstanceOf[String]

  def getIndicatorIconClasses(status: MetalsStatus): String = {
    status match {
      case MetalsLoading => "metals-loading fa-spinner fa-spin"
      case MetalsDisabled => "metals-disabled fa-circle metals-disabled"
      case MetalsReady => "metals-ready fa-circle metals-ready"
      case _: NetworkError => "fa-exclamation-circle"
      case _: MetalsConfigurationError => "fa-exclamation-triangle"
    }
  }

  private def render(props: MetalsStatusIndicator): VdomElement = {
    li(
      title := props.metalsStatus.info,
      role := "button",
      cls := "btn editor metals-status-indicator",
      onClick --> props.toggleMetalsStatus,
    )(
      img(src := metalsLogo),
      span("Metals Status"),
      i(cls := "metals-status-indicator-icon fa", cls := getIndicatorIconClasses(props.metalsStatus))
    )
  }

  private val component =
    ScalaFnComponent
      .withHooks[MetalsStatusIndicator]
      .render(props => MetalsStatusIndicator.render(props))
}
