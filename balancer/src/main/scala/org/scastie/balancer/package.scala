package org.scastie

import akka.actor.ActorSelection
import org.scastie.api.SbtState

package object balancer {
  type SbtBalancer = LoadBalancer[ActorSelection, SbtState]
}
