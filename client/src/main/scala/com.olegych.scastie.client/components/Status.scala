package com.olegych.scastie.client.components

import com.olegych.scastie.api.Inputs
import com.olegych.scastie.api.TaskId
import com.olegych.scastie.client.Page
import com.olegych.scastie.client.StatusState
import japgolly.scalajs.react._

import vdom.all._
import extra.router._

final case class Status(state: StatusState, router: RouterCtl[Page], isAdmin: Boolean, inputs: Inputs) {
  @inline def render: VdomElement = Status.component(this)
}

object Status {

  implicit val reusability: Reusability[Status] =
    Reusability.derive[Status]

  def render(props: Status): VdomElement = {
    def renderSbtTask(tasks: Vector[TaskId]): VdomElement = {
      if (props.isAdmin) {
        if (tasks.isEmpty) {
          div("No Task Running")
        } else {
          ul(
            tasks.zipWithIndex.map {
              case (TaskId(snippetId), j) =>
                li(key := snippetId.toString)(
                  props.router.link(Page.fromSnippetId(snippetId))(
                    s"Task $j"
                  )
                )
            }.toTagMod
          )
        }
      } else {
        div()
      }
    }

    def renderConfiguration(serverInputs: Inputs): VdomElement = {
      val (cssConfig, label) =
        if (serverInputs.needsReload(props.inputs)) {
          ("needs-reload", "Different Configuration")
        } else {
          ("ready", "Same Configuration")
        }

      span(cls := "runner " + cssConfig)(label)
    }

    val sbtRunnersStatus =
      props.state.sbtRunners match {
        case Some(sbtRunners) =>
          div(
            h1("Sbt Runners"),
            ul(
              sbtRunners.zipWithIndex.map {
                case (sbtRunner, i) =>
                  li(key := i)(
                    renderConfiguration(sbtRunner.config),
                    renderSbtTask(sbtRunner.tasks)
                  )
              }.toTagMod
            )
          )
        case _ => div()
      }

    div(sbtRunnersStatus)
  }

  private val component =
    ScalaComponent
      .builder[Status]("Status")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
