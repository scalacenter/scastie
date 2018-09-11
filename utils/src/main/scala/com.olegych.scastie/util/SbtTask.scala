package com.olegych.scastie.util

import com.olegych.scastie.api._

import akka.actor.ActorRef

case class SbtTask(snippetId: SnippetId, inputs: Inputs, ip: String, login: Option[String], progressActor: ActorRef)

case class SbtRun(snippetId: SnippetId, inputs: Inputs, progressActor: ActorRef, snippetActor: ActorRef)

case class Replay(run: SbtRun)

case object SbtUp
