package com.olegych.scastie

import api._

import akka.actor.ActorRef

case class SbtTask(id: Long, inputs: Inputs, ip: String, progressActor: ActorRef)
