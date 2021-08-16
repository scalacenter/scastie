package com.olegych.scastie.sbt

import akka.actor.typed.receptionist.Receptionist
import com.olegych.scastie.util._
import com.olegych.scastie.util.ConfigLoaders._
import com.typesafe.sslconfig.util.{ConfigLoader, EnrichedConfig}
import akka.actor.typed.{Behavior, PostStop, Signal, SupervisorStrategy}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import com.olegych.scastie.util.FormatReq
import com.olegych.scastie.sbt.SbtProcess.SbtTaskEvent

import scala.concurrent.duration.FiniteDuration

object SbtActor {
  type Message = SbtMessage

  def apply(config: SbtConf): Behavior[Message] =
    Behaviors.supervise {
      Behaviors.setup[Message]  { ctx =>
        ctx.system.receptionist ! Receptionist.Register(Services.SbtRunner, ctx.self)

        new SbtActor(config)(ctx)
      }
    }.onFailure(SupervisorStrategy.restart)
}

import SbtActor._
class SbtActor private (
  config: SbtConf
)(ctx: ActorContext[Message]) extends AbstractBehavior[Message](ctx) {
  import context.log

  log.info("*** SbtRunner preStart ***")

  override def onSignal: PartialFunction[Signal, Behavior[Message]] = {
    case PostStop =>
      log.info("*** SbtRunner postStop ***")
      Behaviors.unhandled
  }

  private val formatActor = context.spawn(FormatActor(), name = "FormatActor")

  private val sbtRunner =
    context.spawn(
      SbtProcess(config, Seq("-Xms512m", "-Xmx1g")),
      name = "SbtRunner"
    )

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case format: FormatReq =>
        formatActor ! format

      case task: SbtTask =>
        sbtRunner ! SbtTaskEvent(task)
    }
    this
  }
}

case class SbtConf(
  production: Boolean, // TODO remove
  runTimeout: FiniteDuration,
  sbtReloadTimeout: FiniteDuration,
)

object SbtConf {
  implicit val loader: ConfigLoader[SbtConf] = (c: EnrichedConfig) => SbtConf(
    c.get[Boolean]("production"),
    c.get[FiniteDuration]("runTimeout"),
    c.get[FiniteDuration]("sbtReloadTimeout")
  )
}
