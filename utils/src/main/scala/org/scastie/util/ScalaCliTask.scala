package org.scastie.util

import akka.actor.ActorRef
import org.scastie.api._

case class ScalaCliActorTask(snippetId: SnippetId, inputs: ScalaCliInputs, ip: String, progressActor: ActorRef)
case object StopRunner
case object RunnerTerminated
