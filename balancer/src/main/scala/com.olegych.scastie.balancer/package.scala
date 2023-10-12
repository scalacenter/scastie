package com.olegych.scastie

import akka.actor.ActorSelection
import scastie.api.SbtState

package object balancer {
  type SbtBalancer = LoadBalancer[ActorSelection, SbtState]
}
