package com.olegych.scastie

import akka.actor._
import akka.event.LoggingReceive
import concurrent.duration.FiniteDuration
import akka.actor.Actor._
import com.olegych.scastie.TimeoutActor.StartWatch
import com.olegych.scastie.TimeoutActor.StopWatch
import com.olegych.scastie.TimeoutActor.Kill

/**
  */
private class TimeoutActor(timeout: FiniteDuration, kill: Any => Unit) extends Actor with ActorLogging {
  val messages = collection.mutable.Set[Any]()
  def receive = LoggingReceive {
    case StartWatch(message) =>
      messages += message
      import concurrent.ExecutionContext.Implicits.global
      context.system.scheduler.scheduleOnce(timeout, self, Kill(message))
    case StopWatch(message) =>
      messages -= message
    case Kill(message) =>
      if (messages(message)) {
        messages -= message
        log.info("killing actor while it's processing {}", message)
        kill(message)
      }
  }
}

object TimeoutActor {

  sealed trait TimeoutMessage

  case class StartWatch(message: Any)

  case class StopWatch(message: Any)

  case class Kill(message: Any)

  def apply(timeout: FiniteDuration, kill: Any => Unit)(implicit context: ActorContext) =
    create(context.actorOf(Props(new TimeoutActor(timeout, kill)))) _

  private def create(killer: ActorRef)(r: Receive)(implicit context: ActorContext): Receive = {
    case m =>
      killer ! StartWatch(m)
      r(m)
      killer ! StopWatch(m)
  }
}
