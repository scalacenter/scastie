package com.olegych.scastie.util

import com.olegych.scastie.api._

import akka.actor.ActorRef

case class ScliActorTask(snippetId: SnippetId, inputs: Inputs, ip: String, progressActor: ActorRef)