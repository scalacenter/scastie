package org.scastie.client.components

import org.scastie.api._
import org.scastie.api.TaskId
import org.scastie.client.Page
import org.scastie.client.StatusState
import japgolly.scalajs.react._

import vdom.all._
import extra.router._
import org.scastie.api.BaseInputs
import org.scastie.api.SbtInputs
import org.scastie.api.ScalaCliInputs
import org.scastie.api.ShortInputs

final case class Status(state: StatusState, router: RouterCtl[Page], isAdmin: Boolean, inputs: BaseInputs) {
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

    def renderConfiguration(serverInputs: SbtInputs): VdomElement = {
      val (cssConfig, label) =
        props.inputs match {
          case sbtInputs: SbtInputs if (serverInputs.needsReload(sbtInputs)) => ("needs-reload", "Different Configuration")
          case _: ScalaCliInputs => ("different-target", "sbt runner")
          case _ => ("ready", "Ready sbt runner")
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
