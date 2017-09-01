package com.olegych.scastie.client.components

import com.olegych.scastie.api._
import com.olegych.scastie.client.{View, RestApiClient}

import japgolly.scalajs.react._, vdom.all._, extra._

import org.scalajs.dom

import scalajs.concurrent.JSExecutionContext.Implicits.queue

final case class EmbeddedMenu(isRunning: Boolean,
                              isStatusOk: Boolean,
                              inputs: Inputs,
                              serverUrl: Option[String],
                              run: Reusable[Callback],
                              save: Reusable[CallbackTo[Option[SnippetId]]],
                              setView: View ~=> Callback) {
  @inline def render: VdomElement = EmbeddedMenu.component(this)
}

object EmbeddedMenu {

  implicit val reusability: Reusability[EmbeddedMenu] =
    Reusability.caseClass[EmbeddedMenu]

  private def render(props: EmbeddedMenu): VdomElement = {

    def openScastie: Callback =
      props.save.asCBO.flatMap(
        sid =>
          Callback(
            dom.window
              .open(
                props.serverUrl.getOrElse("") + "/" + sid.url,
                "_blank"
              )
              .focus()
        )
      )

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
        onClick --> openScastie
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[EmbeddedMenu]("EmbeddedMenu")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
