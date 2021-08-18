package com.olegych.scastie

import akka.actor.typed.ActorRef
import com.olegych.scastie.api.SbtState
import com.olegych.scastie.util.SbtMessage

package object balancer {
  type SbtBalancer = LoadBalancer[ActorRef[SbtMessage], SbtState]
}
