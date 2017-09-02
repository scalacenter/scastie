package com.olegych.scastie.client.components

import com.olegych.scastie.api.{Inputs, SbtRunTaskId, EnsimeServerState}
import com.olegych.scastie.client.{StatusState, Page}

import japgolly.scalajs.react._, vdom.all._, extra.router._, extra._

import scala.collection.immutable.Queue

final case class Status(state: StatusState,
                        router: RouterCtl[Page],
                        isAdmin: Boolean,
                        inputs: Inputs) {
  @inline def render: VdomElement = Status.component(this)
}

object Status {

  implicit val reusability: Reusability[Status] =
    Reusability.caseClass[Status]

  def render(props: Status): VdomElement = {
    def renderSbtTask(tasks: Queue[SbtRunTaskId]): VdomElement = {
      if (props.isAdmin) {
        if (tasks.isEmpty) {
          div("No Task Running")
        } else {
          ul(
            tasks.zipWithIndex.map {
              case (SbtRunTaskId(snippetId), j) =>
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

    def renderEnsimeState(state: EnsimeServerState): VdomElement = {
      import EnsimeServerState._

      val label =
        state match {
          case Initializing   => "Initializing"
          case CreatingConfig => "Generating .ensime configuration file"
          case Connecting     => "Connecting to ensime server"
          case Ready          => "Connected"
        }

      val stateCss =
        state match {
          case Initializing   => "initializing"
          case CreatingConfig => "creating-config"
          case Connecting     => "connecting"
          case Ready          => "ready"
        }

      span(cls := "runner-state " + stateCss)(label)
    }

    val ensimeStatus =
      props.state.ensimeRunners match {
        case Some(ensimeRunners) =>
          div(
            h1("Ensime Runners"),
            ul(
              ensimeRunners.zipWithIndex.map {
                case (ensimeRunner, i) => {
                  li(key := i)(
                    renderConfiguration(ensimeRunner.config),
                    renderEnsimeState(ensimeRunner.serverState),
                    ul(
                      ensimeRunner.tasks.map(task => li(task.toString)).toTagMod
                    )
                  )
                }
              }.toTagMod
            )
          )
        case _ => div()
      }

    div(
      sbtRunnersStatus,
      ensimeStatus
    )
  }

  private val component =
    ScalaComponent
      .builder[Status]("Status")
      .render_P(render)
      .configure(Reusability.shouldComponentUpdate)
      .build
}
