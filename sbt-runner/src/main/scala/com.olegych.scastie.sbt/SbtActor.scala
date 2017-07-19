package com.olegych.scastie
package sbt

import api._
import akka.actor.{Actor, ActorSystem, Props}

import scala.concurrent.duration._

class SbtActor(system: ActorSystem,
               runTimeout: FiniteDuration,
               production: Boolean,
               withEnsime: Boolean)
    extends Actor {

  val formatActor =
    context.actorOf(Props(new FormatActor()), name = "FormatActor")

  val sbtRunner =
    context.actorOf(
      Props(new SbtRunner(runTimeout, production, withEnsime)),
      name = "SbtRunner"
    )

  def receive = {
    case SbtPing =>
      sender ! SbtPong

    case req: EnsimeTaskRequest =>
      sbtRunner.forward(req)

    // ensimeActor match {
    //   case Some(ensimeRef) => ensimeRef.forward(req)
    //   case _               => sender ! EnsimeTaskResponse(None, req.taskId)
    // }

    case format: FormatRequest =>
      formatActor.forward(format)

    case task: SbtTask =>
      sbtRunner.forward(task)
  }
}
