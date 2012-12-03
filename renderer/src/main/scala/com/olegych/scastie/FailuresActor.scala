package com.olegych.scastie

import akka.actor.{ActorRef, ActorLogging, Actor}
import com.olegych.scastie.FailuresActor.AddFailure
import akka.event.LoggingReceive

/**
  */
class FailuresActor extends Actor with ActorLogging {
  val failures = collection.mutable.Set[Any]()
  def receive = LoggingReceive {
    case AddFailure(cause, message, sender, content) =>
      log.error(cause, "failed handling {} from {}", message, sender)
      if (failures.add(content)) {
        this.sender.tell(message, sender)
      } else {
        log.info("skipping already failed message {} from {}", message, sender)
      }
  }
}

object FailuresActor {

  sealed trait FailureMessage

  case class AddFailure(cause: Throwable, message: Any, sender: ActorRef, content: Any) extends FailureMessage

}
