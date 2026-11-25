package org.scastie.balancer

import org.scastie.api._

import java.time.Instant
import scala.util.Random

case class Ip(v: String)

case class Task[T <: BaseInputs](config: T, ip: Ip, taskId: TaskId, ts: Instant)

case class TaskHistory[C <: BaseInputs](data: Vector[Task[C]], maxSize: Int) {
  def add(task: Task[C]): TaskHistory[C] = {
    val cappedData = if (data.length < maxSize) data else data.drop(1)
    copy(data = cappedData :+ task)
  }
}

case class Server[R, S, C <: BaseInputs](
    ref: R,
    lastConfig: C,
    state: S,
    mailbox: Vector[Task[C]] = Vector.empty,
    history: TaskHistory[C] = TaskHistory[C](Vector.empty, 1000),
    id: Int = Random.nextInt(),
) {

  def currentTaskId: Option[TaskId] = mailbox.headOption.map(_.taskId)
  def currentConfig: C = mailbox.headOption.map(_.config).getOrElse(lastConfig)
  def configAfterMailbox: C = mailbox.lastOption.map(_.config).getOrElse(lastConfig)

  def done(taskId: TaskId): Server[R, S, C] = {
    val (newMailbox, done) = mailbox.partition(_.taskId != taskId)
    copy(
      lastConfig = done.headOption.map(_.config).getOrElse(lastConfig),
      mailbox = newMailbox,
      history = done.foldLeft(history)(_.add(_)),
    )
  }

  def add(task: Task[C]): Server[R, S, C] = {
    copy(mailbox = mailbox :+ task)
  }
}
