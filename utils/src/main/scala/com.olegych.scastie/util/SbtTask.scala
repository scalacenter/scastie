package com.olegych.scastie.util

import scastie.api._

import akka.actor.ActorRef

case class SbtTask(snippetId: SnippetId, inputs: SbtInputs, ip: String, login: Option[String], progressActor: ActorRef)

case class SbtRun(snippetId: SnippetId, inputs: SbtInputs, progressActor: ActorRef, snippetActor: ActorRef)

case class Replay(run: SbtRun)

case object SbtUp
