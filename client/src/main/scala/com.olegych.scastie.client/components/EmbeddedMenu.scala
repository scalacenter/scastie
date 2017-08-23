package com.olegych.scastie.client.components

import com.olegych.scastie.api._
import com.olegych.scastie.client.{View, RestApiClient}

import japgolly.scalajs.react._, vdom.all._

import org.scalajs.dom

import scalajs.concurrent.JSExecutionContext.Implicits.queue

final case class EmbeddedMenu(isRunning: Boolean,
                              isStatusOk: Boolean,
                              inputs: Inputs,
                              run: Callback,
                              setView: View => Callback) {
  @inline def render: VdomElement = EmbeddedMenu.component(this)
}

object EmbeddedMenu {

  def openScastie(inputs: Inputs): Callback =
    Callback(
      RestApiClient.saveBlocking(inputs).map(sid =>
        dom.window.open(sid.url, "_blank").focus()
      )
    )
   
  private def render(props: EmbeddedMenu): VdomElement = {
    ul(cls := "embedded-menu")(
      RunButton(
        isRunning = props.isRunning,
        isStatusOk = props.isStatusOk,
        run = props.run,
        setView = props.setView
      ).render,
      li(cls := "logo")(
        img(src := Assets.logoUrl),
        span("to Scastie"),
        onClick --> openScastie(props.inputs)
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[EmbeddedMenu]("EmbeddedMenu")
      .render_P(render)
      .build
}
