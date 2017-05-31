package com.olegych.scastie
package client
package components

import japgolly.scalajs.react._, vdom.all._, extra.router._

final case class Status(state: StatusState,
                        router: RouterCtl[Page]) {
  @inline def render: VdomElement = Status.component(this)
}

object Status {
  def render(props: Status) = {
    props.state.runners match {
      case Some(runners) => {
        println(runners)
        ul(
          runners.zipWithIndex.map{ case(runner, i) =>
            li(key := i)(
              span(s"Runner $i: "),
              if(runner.tasks.isEmpty) {
                div("No Task Running")
              }
              else {
                ul(
                  runner.tasks.zipWithIndex.map{ case(snippetId, j) =>
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

      case None =>
        div("Status Unknown")
    }

  }

  private val component =
    ScalaComponent
      .builder[Status]("Status")
      .render_P(render)
      .build
}
