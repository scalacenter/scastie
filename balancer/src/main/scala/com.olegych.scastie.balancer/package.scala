package com.olegych.scastie

import akka.actor.ActorSelection
import com.olegych.scastie.api.{SbtRunTaskId, SbtState}

package object balancer {
  type SbtBalancer = LoadBalancer[SbtRunTaskId, ActorSelection, SbtState]
}
