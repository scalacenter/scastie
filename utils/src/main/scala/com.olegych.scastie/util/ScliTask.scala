package com.olegych.scastie.util

import scastie.api._

import akka.actor.ActorRef

case class ScliActorTask(snippetId: SnippetId, inputs: ScalaCliInputs, ip: String, progressActor: ActorRef)
