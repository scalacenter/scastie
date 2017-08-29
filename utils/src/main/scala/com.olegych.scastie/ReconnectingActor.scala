package com.olegych.scastie

import akka.actor.{Actor, ActorLogging, Cancellable}
import akka.remote.DisassociatedEvent
import com.olegych.scastie.api.ActorConnected

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

case class ReconnectInfo(serverHostname: String,
                         serverAkkaPort: Int,
                         actorHostname: String,
                         actorAkkaPort: Int)

trait ActorReconnecting extends Actor with ActorLogging {

  private var tryReconnectCallback: Option[Cancellable] = None

  val reconnectInfo: Option[ReconnectInfo]

  def tryConnect(): Unit

  def onConnected(): Unit = {}

  def onDisconnected(): Unit = {}

  private def setupReconnectCallback(): Unit = {
    if (reconnectInfo.isDefined) {
      tryReconnectCallback.foreach(_.cancel())
      tryReconnectCallback = Some(
        context.system.scheduler.schedule(0.seconds, 10.seconds) {
          log.info("Reconnecting to server")
          tryConnect()
        }
      )
    }
  }

  override def preStart(): Unit =
    try {
      context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
      setupReconnectCallback()
    } finally super.preStart()

  val reconnectBehavior: Receive = {
    case ActorConnected =>
      log.info("Connected to server")
      tryReconnectCallback.foreach(_.cancel())
      tryReconnectCallback = None
      onConnected()

    case ev: DisassociatedEvent => {
      val isServerHostname =
        reconnectInfo
          .map(info => ev.remoteAddress.host.contains(info.serverHostname))
          .getOrElse(false)

      val isServerAkkaPort =
        reconnectInfo
          .map(info => ev.remoteAddress.port.contains(info.serverAkkaPort))
          .getOrElse(false)

      if (isServerHostname && isServerAkkaPort && ev.inbound) {
        log.warning("Disconnected from server")
        onDisconnected()
        setupReconnectCallback()
      }
    }
  }
}
