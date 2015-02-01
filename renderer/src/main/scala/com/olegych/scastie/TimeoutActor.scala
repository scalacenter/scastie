package com.olegych.scastie

import akka.actor.Actor._
import akka.actor._
import akka.event.LoggingReceive
import com.olegych.scastie.TimeoutActor.{Kill, StartWatch, StopWatch}

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration

/**
  */
private class TimeoutActor(timeout: FiniteDuration, kill: Any => Unit) extends Actor with ActorLogging {
  val messages = collection.mutable.Set[Any]()
  def receive = LoggingReceive {
    case StartWatch(message) =>
      messages += message
      import scala.concurrent.ExecutionContext.Implicits.global
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
      blocking(r(m))
      killer ! StopWatch(m)
  }
}
