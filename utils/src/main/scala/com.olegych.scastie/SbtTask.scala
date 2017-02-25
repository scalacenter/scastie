package com.olegych.scastie

import api._

import akka.actor.ActorRef

case class SbtTask(snippetId: SnippetId,
                   inputs: Inputs,
                   ip: String,
                   login: Option[String],
                   progressActor: ActorRef)
