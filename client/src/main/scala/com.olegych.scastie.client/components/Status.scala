package com.olegych.scastie.client.components

import com.olegych.scastie.api.{Inputs, SbtRunTaskId}
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

    def needsReload(serverInputs: Inputs): TagMod = {
      val resClass =
        if (serverInputs.needsReload(props.inputs)) {
          "needs-reload"
        } else {
          "ready"
        }

      TagMod(cls := resClass + " runner")
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
                    span(needsReload(sbtRunner.config))(s"Sbt $i "),
                    renderSbtTask(sbtRunner.tasks)
                  )
              }.toTagMod
            )
          )
        case _ => div()
      }

    val ensimeStatus =
      props.state.ensimeRunners match {
        case Some(ensimeRunners) =>
          div(
            h1("Ensime Runners"),
            ul(
              ensimeRunners.zipWithIndex.map {
                case (ensimeRunner, i) =>
                  li(key := i)(
                    span(needsReload(ensimeRunner.config))(
                      s"Ensime $i ${ensimeRunner.serverState}" +
                        ("*" * ensimeRunner.tasks.size)
                    )
                  )
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
