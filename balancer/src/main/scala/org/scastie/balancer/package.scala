package org.scastie

import akka.actor.ActorSelection
import org.scastie.api.ServerState

package object balancer {
  type SbtBalancer = LoadBalancer[ActorSelection, ServerState]
}
