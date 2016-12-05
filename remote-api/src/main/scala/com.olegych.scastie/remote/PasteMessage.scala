package com.olegych.scastie
package remote

import akka.actor.ActorRef

case class PasteProgress(
    id: Long,
    done: Boolean,
    output: String,
    compilationInfos: List[api.Problem],
    instrumentations: List[api.Instrumentation]
)

case class RunPaste(
  id: Long,
  code: String,
  sbtConfig: String,
  scalaTargetType: api.ScalaTargetType,
  progressActor: ActorRef
)

case class RunPasteError(
  id: Long,
  cause: String
)
