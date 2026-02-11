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

import org.scastie.client.i18n.I18n
import japgolly.scalajs.react.hooks.HookCtx.I18

final case class Status(state: StatusState, router: RouterCtl[Page], isAdmin: Boolean, inputs: BaseInputs, language: String) {
  @inline def render: VdomElement = Status.component(this)
}

object Status {

  implicit val reusability: Reusability[Status] =
    Reusability.derive[Status]

  def render(props: Status): VdomElement = {
    def renderSbtTask(tasks: Vector[TaskId]): VdomElement = {
      if (tasks.isEmpty) {
        div()
      } else {
        ul(cls := "task-list")(
          tasks.zipWithIndex.map {
            case (TaskId(snippetId), j) =>
              li(key := j)(
                if (props.isAdmin) {
                  props.router.link(Page.fromSnippetId(snippetId))(
                    s"${I18n.t("status.task")} $j"
                  )
                } else {
                  span(s"${I18n.t("status.task")} $j")
                }
              )
          }.toTagMod
        )
      }
    }

    def renderRunnerStatus(hasRunningTask: Boolean): (String, String) = {
      if (hasRunningTask) {
        ("running", I18n.t("status.running"))
      } else {
        ("idle", I18n.t("status.idle"))
      }
    }

    def renderConfigurationSbt(runnerState: SbtRunnerState): VdomElement = {
      val (cssConfig, label) =
        props.inputs match {
          case sbtInputs: SbtInputs if (runnerState.config.needsReload(sbtInputs)) =>
            ("needs-reload", I18n.t("status.different_config"))
          case _: ScalaCliInputs =>
            ("different-target", I18n.t("status.different_target"))
          case _ =>
            renderRunnerStatus(runnerState.hasRunningTask)
        }

      span(cls := "runner " + cssConfig)(label)
    }

    def renderConfigurationCli(runnerState: ScalaCliRunnerState): VdomElement = {
      val (cssConfig, label) = props.inputs match {
        case _: SbtInputs =>
          ("different-target", I18n.t("status.different_target"))
        case _ =>
          renderRunnerStatus(runnerState.hasRunningTask)
      }

      span(cls := "runner " + cssConfig)(label)
    }

    val scalaCliRunnersStatus =
      props.state.scalaCliRunners match {
        case Some(scalaCliRunners) =>
          div(
            h1(I18n.t("status.scala_cli_runners")),
            ul(
              scalaCliRunners.zipWithIndex.map {
                case (scalaCliRunner, i) =>
                  li(key := i)(
                    renderConfigurationCli(scalaCliRunner),
                    renderSbtTask(scalaCliRunner.tasks)
                  )
              }.toTagMod
            )
          )
        case _ => div()
      }

    val sbtRunnersStatus =
      props.state.sbtRunners match {
        case Some(sbtRunners) =>
          div(
            marginBottom := "4rem",
            h1(I18n.t("status.sbt_runners")),
            ul(
              sbtRunners.zipWithIndex.map {
                case (sbtRunner, i) =>
                  li(key := i)(
                    renderConfigurationSbt(sbtRunner),
                    renderSbtTask(sbtRunner.tasks)
                  )
              }.toTagMod
            )
          )
        case _ => div()
      }

    div(
      sbtRunnersStatus,
      scalaCliRunnersStatus
    )
  }

  private val component =
    ScalaComponent
      .builder[Status]("Status")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
