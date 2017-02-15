package com.olegych.scastie

import api._

import akka.actor.ActorRef

case class SbtTask(id: Int,
                   inputs: Inputs,
                   ip: String,
                   login: String,
                   progressActor: ActorRef)
