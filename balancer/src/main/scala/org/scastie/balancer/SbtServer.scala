package org.scastie.balancer

import scala.util.Random

import org.scastie.api._

case class SbtServer[R, S](
    ref: R,
    lastConfig: SbtInputs,
    state: S,
    mailbox: Vector[Task[SbtInputs]] = Vector.empty,
    history: TaskHistory = TaskHistory(Vector.empty, 1000),
    id: Int = Random.nextInt()
) {

  def currentTaskId: Option[TaskId] = mailbox.headOption.map(_.taskId)
  def currentConfig: SbtInputs = mailbox.headOption.map(_.config).getOrElse(lastConfig)

  def done(taskId: TaskId): SbtServer[R, S] = {
    val (newMailbox, done) = mailbox.partition(_.taskId != taskId)
    copy(
      lastConfig = done.headOption.map(_.config).getOrElse(lastConfig),
      mailbox = newMailbox,
      history = done.foldLeft(history)(_.add(_))
    )
  }

  def add(task: Task[SbtInputs]): SbtServer[R, S] = {
    copy(mailbox = mailbox :+ task)
  }

}
