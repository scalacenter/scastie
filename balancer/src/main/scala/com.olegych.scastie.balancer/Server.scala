package com.olegych.scastie.balancer

import com.olegych.scastie.api._

import scala.util.Random

case class Server[R, S](
    ref: R,
    lastConfig: Inputs,
    state: S,
    mailbox: Vector[Task] = Vector.empty,
    history: TaskHistory = TaskHistory(Vector.empty, 1000),
    id: Int = Random.nextInt(),
) {

  def currentTaskId: Option[TaskId] = mailbox.headOption.map(_.taskId)
  def currentConfig: Inputs = mailbox.headOption.map(_.config).getOrElse(lastConfig)

  def done(taskId: TaskId): Server[R, S] = {
    val (newMailbox, done) = mailbox.partition(_.taskId != taskId)
    copy(
      lastConfig = done.headOption.map(_.config).getOrElse(lastConfig),
      mailbox = newMailbox,
      history = done.foldLeft(history)(_.add(_)),
    )
  }

  def add(task: Task): Server[R, S] = {
    copy(mailbox = mailbox :+ task)
  }
}
