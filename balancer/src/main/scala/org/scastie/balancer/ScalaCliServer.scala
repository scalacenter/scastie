package org.scastie.balancer

import org.scastie.api._

import scala.util.Random

case class ScalaCliServer[R, S](
    ref: R,
    lastConfig: ScalaCliInputs,
    state: S,
    mailbox: Vector[Task[ScalaCliInputs]] = Vector.empty,
    id: Int = Random.nextInt(),
) {

  def currentTaskId: Option[TaskId] = mailbox.headOption.map(_.taskId)
  def currentConfig: ScalaCliInputs = mailbox.headOption.map(_.config).getOrElse(lastConfig)

  def done(taskId: TaskId): ScalaCliServer[R, S] = {
    val (newMailbox, done) = mailbox.partition(_.taskId != taskId)
    copy(
      lastConfig = done.headOption.map(_.config).getOrElse(lastConfig),
      mailbox = newMailbox
    )
  }

  def add(task: Task[ScalaCliInputs]): ScalaCliServer[R, S] = {
    copy(mailbox = mailbox :+ task)
  }
}
