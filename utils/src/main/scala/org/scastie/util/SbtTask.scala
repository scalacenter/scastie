package org.scastie.util

import org.scastie.api._

import org.apache.pekko.actor.ActorRef

case class SbtTask(snippetId: SnippetId, inputs: SbtInputs, ip: String, login: Option[String], progressActor: ActorRef)

case class SbtRun(snippetId: SnippetId, inputs: SbtInputs, progressActor: ActorRef, snippetActor: ActorRef)

case class Replay(run: SbtRun)

case object SbtUp
