package com.olegych.scastie.client.components

import com.olegych.scastie.api._
import com.olegych.scastie.client.View
import japgolly.scalajs.react._
import org.scalajs.dom

import vdom.all._

final case class EmbeddedMenu(isRunning: Boolean,
                              inputs: Inputs,
                              inputsHasChanged: Boolean,
                              embeddedSnippetId: Option[SnippetId],
                              serverUrl: Option[String],
                              run: Reusable[Callback],
                              save: Reusable[CallbackTo[Option[SnippetId]]],
                              setView: View ~=> Callback) {
  @inline def render: VdomElement = EmbeddedMenu.component(this)
}

object EmbeddedMenu {

  implicit val reusability: Reusability[EmbeddedMenu] =
    Reusability.derive[EmbeddedMenu]

  private def render(props: EmbeddedMenu): VdomElement = {

    val urlBase = props.serverUrl.getOrElse("")

    def openScastie: Callback = {

      def open(snippetId: SnippetId): Callback = {
        Callback(
          dom.window
            .open(
              urlBase + "/" + snippetId.url,
              "_blank"
            )
            .focus()
        )

      }

      println("props.embeddedSnippetId: " + props.embeddedSnippetId)
      println("props.inputsHasChanged: " + props.inputsHasChanged)

      props.embeddedSnippetId match {
        case Some(snippetId) if !props.inputsHasChanged => {
          open(snippetId)
        }

        case _ => {
          props.save.asCBO.flatMap(open)
        }
      }

    }

    ul(cls := "embedded-menu")(
      RunButton(
        isRunning = props.isRunning,
        isStatusOk = true,
        save = props.run,
        setView = props.setView,
        embedded = true,
      ).render,
      li(cls := "logo")(
        img(src := Assets.logo),
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
