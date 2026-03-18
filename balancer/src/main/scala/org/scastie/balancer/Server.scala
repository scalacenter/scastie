package org.scastie.balancer

import org.scastie.api._

import java.time.Instant
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

case class Ip(v: String)

case class Task[T <: BaseInputs](config: T, ip: Ip, taskId: TaskId, ts: Instant, lastSeen: Instant = Instant.now)

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

  def refreshTaskLastSeen(taskId: TaskId, now: Instant = Instant.now): Server[R, S, C] = {
    copy(mailbox = mailbox.map(t => if (t.taskId == taskId) t.copy(lastSeen = now) else t))
  }

  def cleanUpStaleTasks(maxAge: FiniteDuration): Server[R, S, C] = {
    val cutoff = Instant.now.minusMillis(maxAge.toMillis)
    if (mailbox.forall(_.lastSeen.isAfter(cutoff))) this
    else {
      val (keep, stale) = mailbox.partition(_.lastSeen.isAfter(cutoff))
      copy(
        lastConfig = stale.lastOption.map(_.config).getOrElse(lastConfig),
        mailbox = keep,
        history = stale.foldLeft(history)(_.add(_)),
      )
    }
  }
}
