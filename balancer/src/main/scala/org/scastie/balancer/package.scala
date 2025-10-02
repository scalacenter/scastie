package org.scastie

import org.scastie.api.ServerState

import akka.actor.ActorSelection

package object balancer {
  type SbtBalancer = LoadBalancer[ActorSelection, ServerState]
}
