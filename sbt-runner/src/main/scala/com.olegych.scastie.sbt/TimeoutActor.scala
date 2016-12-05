package com.olegych.scastie
package sbt

import TimeoutActor._

import akka.actor.Actor._
import akka.actor.{Kill => _, _}
import akka.event.LoggingReceive

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration

private class TimeoutActor(timeout: FiniteDuration, kill: Any => Unit)
    extends Actor
    with ActorLogging {
  import context._

  val messages = collection.mutable.Set[Any]()
  def receive = LoggingReceive {
    case StartWatch(message) => {
      messages += message
      context.system.scheduler.scheduleOnce(timeout, self, Kill(message))
      ()
    }

    case StopWatch(message) => {
      messages -= message
      ()
    }

    case Kill(message) => {
      if (messages(message)) {
        messages -= message
        log.info("killing actor while it's processing {}", message)
        kill(message)
      }
    }
  }
}

object TimeoutActor {

  sealed trait TimeoutMessage

  case class StartWatch(message: Any)

  case class StopWatch(message: Any)

  case class Kill(message: Any)

  def apply(actorName: String, timeout: FiniteDuration, kill: Any => Unit)(
      implicit context: ActorContext) =
    create(
      context.actorOf(Props(new TimeoutActor(timeout, kill)),
                      name = actorName)) _

  private def create(killer: ActorRef)(r: Receive)(
      implicit context: ActorContext): Receive = {
    case m =>
      killer ! StartWatch(m)
      blocking(r(m))
      killer ! StopWatch(m)
  }
}
