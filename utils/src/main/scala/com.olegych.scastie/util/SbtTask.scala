package com.olegych.scastie.util

import com.olegych.scastie.api._
import akka.NotUsed
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.ServiceKey

sealed trait SbtMessage
// note: DispatchActor.Message alias to this
trait BalancerMessage

case class FormatReq(replyTo: ActorRef[FormatResponse], r: FormatRequest) extends SbtMessage with BalancerMessage

case class SnippetProgressAsk(replyTo: ActorRef[NotUsed], v: SnippetProgress) extends BalancerMessage

case class SbtTask(
  snippetId: SnippetId,
  inputs: Inputs,
  ip: String,
  login: Option[String],
  progressActor: ActorRef[SnippetProgress],
  snippetActor: ActorRef[SnippetProgressAsk],
) extends SbtMessage

object Services {
  val SbtRunner: ServiceKey[SbtMessage] = ServiceKey("SbtRunner")
  val Balancer: ServiceKey[BalancerMessage] = ServiceKey("Balancer")
}
