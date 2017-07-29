package com.olegych.scastie

import com.olegych.scastie.proto.{SnippetId, Inputs}

import akka.actor.ActorRef

case class SbtTask(
    snippetId: SnippetId,
    inputs: Inputs,
    ip: String,
    login: Option[String],
    progressActor: ActorRef
)
