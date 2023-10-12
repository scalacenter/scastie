package org.scastie.util

import org.scastie.api._

import akka.actor.ActorRef

case class ScliActorTask(snippetId: SnippetId, inputs: ScalaCliInputs, ip: String, progressActor: ActorRef)
