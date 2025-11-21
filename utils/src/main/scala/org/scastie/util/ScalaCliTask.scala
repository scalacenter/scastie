package org.scastie.util

import org.scastie.api._

import akka.actor.ActorRef

case class ScalaCliTask(snippetId: SnippetId, inputs: ScalaCliInputs, ip: String, progressActor: ActorRef)
case object StopRunner
case object RunnerTerminated
