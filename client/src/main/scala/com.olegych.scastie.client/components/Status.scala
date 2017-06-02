package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._, vdom.all._, extra.router._

final case class Status(state: StatusState, router: RouterCtl[Page], isAdmin: Boolean) {
  @inline def render: VdomElement = Status.component(this)
}

object Status {
  def render(props: Status): VdomElement = {
    props.state.runners match {
      case Some(runners) if props.isAdmin => {
        ul(
          runners.zipWithIndex.map {
            case (runner, i) =>
              li(key := i)(
                span(s"Runner $i: "),
                if (runner.tasks.isEmpty) {
                  div("No Task Running")
                } else {
                  ul(
                    runner.tasks.zipWithIndex.map {
                      case (snippetId, j) =>
                        li(key := snippetId.toString)(
                          props.router.link(Page.fromSnippetId(snippetId))(
                            s"Task $j"
                          )
                        )
                    }.toTagMod
                  )
                }
              )
          }.toTagMod
        )
      }
      case _ => div()
    }

  }

  private val component =
    ScalaComponent
      .builder[Status]("Status")
      .render_P(render)
      .build
}
