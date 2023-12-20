package org.scastie.util

import org.scastie.api._

import akka.actor.ActorRef

case class ScalaCliActorTask(snippetId: SnippetId, inputs: ScalaCliInputs, ip: String, progressActor: ActorRef)
case object StopRunner
case object RunnerTerminated
