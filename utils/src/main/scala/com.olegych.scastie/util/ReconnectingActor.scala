package com.olegych.scastie.util

import akka.actor.{Actor, ActorContext, ActorLogging, Cancellable}
import akka.remote.DisassociatedEvent
import com.olegych.scastie.api.ActorConnected

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

case class ReconnectInfo(serverHostname: String, serverAkkaPort: Int, actorHostname: String, actorAkkaPort: Int)

trait ActorReconnecting extends Actor with ActorLogging {

  private var tryReconnectCallback: Option[Cancellable] = None

  def reconnectInfo: Option[ReconnectInfo]

  def tryConnect(context: ActorContext): Unit

  def onConnected(): Unit = {}

  def onDisconnected(): Unit = {}

  private def setupReconnectCallback(context: ActorContext): Unit = {
    if (reconnectInfo.isDefined) {
      tryReconnectCallback.foreach(_.cancel())
      tryReconnectCallback = Some(
        context.system.scheduler.schedule(0.seconds, 10.seconds) {
          log.info("Reconnecting to server")
          tryConnect(context)
        }
      )
    }
  }

  override def preStart(): Unit =
    try {
      context.system.eventStream.subscribe(self, classOf[DisassociatedEvent])
      setupReconnectCallback(context)
    } finally super.preStart()

  val reconnectBehavior: Receive = {
    case ActorConnected =>
      log.info("Connected to server")
      tryReconnectCallback.foreach(_.cancel())
      tryReconnectCallback = None
      onConnected()

    case ev: DisassociatedEvent => {
      println("DisassociatedEvent " + ev)

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
        setupReconnectCallback(context)
      }
    }
  }
}
