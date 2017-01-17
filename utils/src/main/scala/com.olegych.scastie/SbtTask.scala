package com.olegych.scastie

import api._

import akka.actor.ActorRef

case class SbtTask(id: Long, inputs: Inputs, progressActor: ActorRef)
