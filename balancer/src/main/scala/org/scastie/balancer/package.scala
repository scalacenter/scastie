package org.scastie

import org.apache.pekko.actor.ActorSelection
import org.scastie.api.{SbtInputs, ScalaCliInputs, ServerState}

package object balancer {
  type SbtServer[R, S] = Server[R, S, SbtInputs]
  type ScalaCliServer[R, S] = Server[R, S, ScalaCliInputs]

  type SbtBalancer = SbtLoadBalancer[ActorSelection, ServerState]
  type ScalaCliBalancer = ScalaCliLoadBalancer[ActorSelection, ServerState]
}
