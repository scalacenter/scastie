package com.olegych.scastie
package sbt

import api._
import akka.actor.{Actor, ActorSystem, Props}

import scala.concurrent.duration._

class SbtActor(system: ActorSystem,
               runTimeout: FiniteDuration,
               production: Boolean)
    extends Actor {

  val formatActor =
    context.actorOf(Props(new FormatActor()), name = "FormatActor")

  val sbtRunner =
    context.actorOf(
      Props(new SbtRunner(runTimeout, production)),
      name = "SbtRunner"
    )

  val ensimeActor =
    context.actorOf(
      Props(new EnsimeActor(system, sbtRunner)),
      name = "EnsimeActor"
    )

  def receive = {
    case SbtPing =>
      sender ! SbtPong

    case completion: CompletionRequest =>
      ensimeActor.forward(completion)

    case format: FormatRequest =>
      formatActor.forward(format)

    case task: SbtTask =>
      sbtRunner.forward(task)
  }
}
