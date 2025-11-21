package org.scastie

import akka.actor.ActorSelection
import org.scastie.api.ServerState

package object balancer {
  type SbtBalancer = SbtLoadBalancer[ActorSelection, ServerState]
  type ScalaCliBalancer = ScalaCliLoadBalancer[ActorSelection, ServerState]
}
