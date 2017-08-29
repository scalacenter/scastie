package com.olegych.scastie.util

import scala.concurrent.duration._

import java.util.concurrent.{Callable, FutureTask, TimeUnit, TimeoutException}

object TaskTimeout {
  def apply[T](duration: Duration, task: ⇒ T, onTimeout: => T): T = {
    val task0 = new FutureTask(new Callable[T]() {
      def call: T = task
    })
    val thread = new Thread(task0)
    try {
      thread.start()
      task0.get(duration.toMillis, TimeUnit.MILLISECONDS)
    } catch {
      case _: TimeoutException ⇒ onTimeout
    } finally {
      if (thread.isAlive) thread.stop()
    }
  }
}
