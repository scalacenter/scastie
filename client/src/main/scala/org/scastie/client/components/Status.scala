package org.scastie.client.components

import org.scastie.api._
import org.scastie.api.BaseInputs
import org.scastie.api.SbtInputs
import org.scastie.api.ScalaCliInputs
import org.scastie.api.ShortInputs
import org.scastie.api.TaskId
import org.scastie.client.i18n.I18n
import org.scastie.client.Page
import org.scastie.client.StatusState

import japgolly.scalajs.react._
import japgolly.scalajs.react.hooks.HookCtx.I18

import extra.router._
import vdom.all._

final case class Status(
    state: StatusState,
    router: RouterCtl[Page],
    isAdmin: Boolean,
    inputs: BaseInputs,
    language: String
) {
  @inline def render: VdomElement = Status.component(this)
}

object Status {

  implicit val reusability: Reusability[Status] = Reusability.derive[Status]

  def render(props: Status): VdomElement = {
    def renderSbtTask(tasks: Vector[TaskId]): VdomElement = {
      if (props.isAdmin) {
        if (tasks.isEmpty) {
          div(I18n.t("status.no_task"))
        } else {
          ul(
            tasks.zipWithIndex.map { case (TaskId(snippetId), j) =>
              li(key := snippetId.toString)(
                props.router.link(Page.fromSnippetId(snippetId))(
                  s"${I18n.t("status.task")} $j"
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
      val (cssConfig, label) = props.inputs match {
        case sbtInputs: SbtInputs if (serverInputs.needsReload(sbtInputs)) =>
          ("needs-reload", I18n.t("status.different_config"))
        case _: ScalaCliInputs => ("different-target", I18n.t("status.sbt_runner_config"))
        case _                 => ("ready", I18n.t("status.same_config"))
      }

      span(cls := "runner " + cssConfig)(label)
    }

    val sbtRunnersStatus = props.state.sbtRunners match {
      case Some(sbtRunners) => div(
          h1(I18n.t("status.sbt_runners")),
          ul(
            sbtRunners.zipWithIndex.map { case (sbtRunner, i) =>
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

  private val component = ScalaComponent
    .builder[Status]("Status")
    .render_P(render)
    .configure(Reusability.shouldComponentUpdate)
    .build

}
