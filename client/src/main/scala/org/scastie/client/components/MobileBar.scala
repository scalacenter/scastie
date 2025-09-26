package org.scastie.client.components

import org.scastie.client.View
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.all._

final case class MobileBar(isRunning: Boolean,
                           isStatusOk: Boolean,
                           isDarkTheme: Boolean,
                           save: Reusable[Callback],
                           setView: View ~=> Callback,
                           isNewSnippetModalClosed: Boolean,
                           clear: Reusable[Callback],
                           openNewSnippetModal: Reusable[Callback],
                           closeNewSnippetModal: Reusable[Callback],
                           newSnippet: Reusable[Callback],
                           forceDesktop: Reusable[Callback],
                           language: String) {
  @inline def render: VdomElement = MobileBar.component(this)
}

object MobileBar {
  implicit val reusability: Reusability[MobileBar] =
    Reusability.derive[MobileBar]

  private def render(props: MobileBar): VdomElement = {
    nav(cls := "editor-mobile")(
      ul(cls := "editor-buttons")(
        RunButton(
          isRunning = props.isRunning,
          isStatusOk = props.isStatusOk,
          save = props.save,
          setView = props.setView,
          embedded = false,
        ).render,
        NewButton(
          isDarkTheme = props.isDarkTheme,
          isNewSnippetModalClosed = props.isNewSnippetModalClosed,
          openNewSnippetModal = props.openNewSnippetModal,
          closeNewSnippetModal = props.closeNewSnippetModal,
          newSnippet = props.newSnippet,
          language = props.language
        ).render,
        ClearButton(
          clear = props.clear,
          language = props.language
        ).render,
        //this doesn't work too well, better use browsers 'request desktop site'
//        DesktopButton(
//          forceDesktop = props.forceDesktop
//        ).render
      )
    )
  }

  private val component =
    ScalaComponent
      .builder[MobileBar]("MobileBar")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
