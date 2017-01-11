package com.olegych.scastie
package remote

import akka.actor.ActorRef

case class RunPaste(
    id: Long,
    code: String,
    sbtConfig: String,
    sbtPluginsConfig: String,
    scalaTargetType: api.ScalaTargetType,
    progressActor: ActorRef
)

case class RunPasteError(
    id: Long,
    cause: String
)
