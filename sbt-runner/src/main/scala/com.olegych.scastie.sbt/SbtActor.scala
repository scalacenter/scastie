package com.olegych.scastie
package sbt

import api._
import akka.actor.{Actor, ActorSystem, Props, ActorRef}

import scala.concurrent.duration._

class SbtActor(system: ActorSystem,
               runTimeout: FiniteDuration,
               production: Boolean,
               withEnsime: Boolean,
               readyRef: Option[ActorRef])
    extends Actor {

  private val formatActor =
    context.actorOf(Props(new FormatActor()), name = "FormatActor")

  private val sbtRunner =
    context.actorOf(
      Props(new SbtRunner(runTimeout, production)),
      name = "SbtRunner"
    )

  private val ensimeActor =
    if (withEnsime) {
      Some(
        context.actorOf(
          Props(new EnsimeActor(context.system, sbtRunner, readyRef)),
          name = "EnsimeActor"
        )
      )
    } else None

  override def receive: Receive = {
    case SbtPing =>
      sender ! SbtPong

    case req: EnsimeTaskRequest =>
      ensimeActor match {
        case Some(ensimeRef) => ensimeRef.forward(req)
        case _               => sender ! EnsimeTaskResponse(None, req.taskId)
      }

    case format: FormatRequest =>
      formatActor.forward(format)

    case task: SbtTask =>
      sbtRunner.forward(task)
  }
}
