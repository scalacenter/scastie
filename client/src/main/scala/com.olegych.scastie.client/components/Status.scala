package com.olegych.scastie.client.components

import com.olegych.scastie.api.Inputs
import com.olegych.scastie.api.TaskId
import com.olegych.scastie.client.Page
import com.olegych.scastie.client.StatusState
import japgolly.scalajs.react._

import vdom.all._
import extra.router._

import com.olegych.scastie.client.i18n.I18n
import japgolly.scalajs.react.hooks.HookCtx.I18

final case class Status(state: StatusState, router: RouterCtl[Page], isAdmin: Boolean, inputs: Inputs, language: String) {
  @inline def render: VdomElement = Status.component(this)
}

object Status {

  implicit val reusability: Reusability[Status] =
    Reusability.derive[Status]

  def render(props: Status): VdomElement = {
    def renderSbtTask(tasks: Vector[TaskId]): VdomElement = {
      if (props.isAdmin) {
        if (tasks.isEmpty) {
          div(I18n.t("status.no_task"))
        } else {
          ul(
            tasks.zipWithIndex.map {
              case (TaskId(snippetId), j) =>
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

    def renderConfiguration(serverInputs: Inputs): VdomElement = {
      val (cssConfig, label) =
        if (serverInputs.needsReload(props.inputs)) {
          ("needs-reload", I18n.t("status.different_config"))
        } else {
          ("ready", I18n.t("status.same_config"))
        }

      span(cls := "runner " + cssConfig)(label)
    }

    val sbtRunnersStatus =
      props.state.sbtRunners match {
        case Some(sbtRunners) =>
          div(
            h1(I18n.t("status.sbt_runners")),
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
