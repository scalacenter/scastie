package com.olegych.scastie.balancer

import com.olegych.scastie.api._

import scala.concurrent.duration.{FiniteDuration, DurationInt}
import scala.collection.immutable.Queue

case class Server[C, T <: TaskId, R, S](ref: R,
                                        lastConfig: C,
                                        mailbox: Queue[Task[C, T]],
                                        state: S) {

  def currentTaskId: Option[T] = mailbox.headOption.map(_.taskId)
  def currentConfig: C = mailbox.headOption.map(_.config).getOrElse(lastConfig)

  def done: Server[C, T, R, S] = {
    val (task, mailbox0) = mailbox.dequeue

    assert(currentTaskId.contains(task.taskId))

    copy(
      lastConfig = task.config,
      mailbox = mailbox0
    )
  }

  def add(task: Task[C, T]): Server[C, T, R, S] = {
    copy(mailbox = mailbox.enqueue(task))
  }

  def cost(taskCost: FiniteDuration,
           reloadCost: FiniteDuration,
           needsReload: (C, C) => Boolean): FiniteDuration = {
    import Server._

    val reloadsPenalties =
      mailbox.sliding(2).foldLeft(0.seconds) { (acc, slide) =>
        val reloadPenalty =
          slide match {
            case Queue(x, y) if needsReload(x.config, y.config) => reloadCost
            case _                                              => 0.seconds
          }
        acc + reloadPenalty
      }

    def sum(durations: Queue[FiniteDuration]): FiniteDuration = {
      durations.foldLeft(0.seconds)(_ + _)
    }

    reloadsPenalties + (mailbox.size * taskCost)
  }
}

object Server {
  def of[T <: TaskId]: ServerOf[T] = new ServerOf[T]
}

class ServerOf[T <: TaskId] {
  def apply[C, R, S](ref: R, config: C, state: S): Server[C, T, R, S] =
    Server(
      ref = ref,
      lastConfig = config,
      mailbox = Queue.empty,
      state = state
    )
}