package com.olegych.scastie

import com.olegych.scastie.api.{
  Inputs,
  SbtRunTaskId,
  EnsimeTaskId,
  EnsimeServerState,
  SbtState
}

import akka.actor.ActorSelection

package object balancer {
  type SbtBalancer =
    LoadBalancer[Inputs, SbtRunTaskId, ActorSelection, SbtState]

  type EnsimeBalancer =
    LoadBalancer[Inputs, EnsimeTaskId, ActorSelection, EnsimeServerState]
}
