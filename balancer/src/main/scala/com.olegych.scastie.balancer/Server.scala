package com.olegych.scastie.balancer

import com.olegych.scastie.api._

import scala.concurrent.duration.{FiniteDuration, DurationInt}
import scala.collection.immutable.Queue

case class Server[T <: TaskId, R, S](ref: R, lastConfig: Inputs, mailbox: Queue[Task[T]], state: S) {

  def currentTaskId: Option[T] = mailbox.headOption.map(_.taskId)
  def currentConfig: Inputs = mailbox.headOption.map(_.config).getOrElse(lastConfig)

  def done(taskId: T): Server[T, R, S] = {
    val (newMailbox, done) = mailbox.partition(_.taskId != taskId)
    copy(
      lastConfig = done.headOption.map(_.config).getOrElse(lastConfig),
      mailbox = newMailbox,
    )
  }

  def add(task: Task[T]): Server[T, R, S] = {
    copy(mailbox = mailbox.enqueue(task))
  }

  def cost(taskCost: FiniteDuration, reloadCost: FiniteDuration): FiniteDuration = {

    val reloadsPenalties =
      mailbox.sliding(2).foldLeft(0.seconds) { (acc, slide) =>
        val reloadPenalty =
          slide match {
            case Queue(x, y) if x.config.needsReload(y.config) => reloadCost
            case _                                             => 0.seconds
          }
        acc + reloadPenalty
      }

    reloadsPenalties + (mailbox.size * taskCost)
  }
}

object Server {
  def of[T <: TaskId]: ServerOf[T] = new ServerOf[T]
}

class ServerOf[T <: TaskId] {
  def apply[R, S](ref: R, config: Inputs, state: S): Server[T, R, S] =
    Server(
      ref = ref,
      lastConfig = config,
      mailbox = Queue.empty,
      state = state
    )
}
