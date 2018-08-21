package com.olegych.scastie

import com.olegych.scastie.api.{Inputs, SbtRunTaskId, SbtState}

import akka.actor.ActorSelection

package object balancer {
  type SbtBalancer =
    LoadBalancer[Inputs, SbtRunTaskId, ActorSelection, SbtState]
}
