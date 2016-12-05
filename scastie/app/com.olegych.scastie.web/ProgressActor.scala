package com.olegych.scastie
package web

import remote.PasteProgress

import play.api.libs.iteratee.Concurrent.Channel

import akka.actor.{ActorLogging, Actor}
import play.api.libs.iteratee.{Concurrent, Enumerator, Iteratee}

import collection.mutable
import akka.event.LoggingReceive

import upickle.default.{write => uwrite}

class ProgressActor extends Actor with ActorLogging {
  import Progress._

  private val monitors = new mutable.HashMap[Long, mutable.Set[MonitorChannel]]
  with mutable.MultiMap[Long, MonitorChannel]
  private val progressBuffer = mutable.Map[Long, PasteProgress]()

  def receive = LoggingReceive {
    case MonitorProgress(id) => {
      val (enumerator, channel) = Concurrent.broadcast[String]
      val monitorChannel        = MonitorChannel(id, null, channel)
      import concurrent.ExecutionContext.Implicits.global
      val iteratee = Iteratee.ignore[String].map { _ =>
        self ! StopMonitorProgress(monitorChannel)
      }
      monitors.addBinding(id, monitorChannel)
      sender ! monitorChannel.copy(value = iteratee -> enumerator)
      progressBuffer.get(id).foreach(sendProgress)
    }

    case StopMonitorProgress(monitorChannel) => {
      monitors.removeBinding(monitorChannel.id, monitorChannel)
      ()
    }

    case pasteProgress: PasteProgress => {
      sendProgress(pasteProgress)
    }
  }

  private def sendProgress(pasteProgress: PasteProgress): Unit = {
    val monitorChannels = monitors.get(pasteProgress.id).toList.flatten
    if (monitorChannels.isEmpty) {
      progressBuffer += (pasteProgress.id -> pasteProgress)
    } else {
      progressBuffer.remove(pasteProgress.id)
      // monitorChannels.map(_.channel.push(uwrite(pasteProgress)))
    }

    val apiModel = api.PasteProgress(
      id = pasteProgress.id,
      output = pasteProgress.output,
      compilationInfos = pasteProgress.compilationInfos,
      instrumentations = pasteProgress.instrumentations
    )
    monitorChannels.foreach(_.channel.push(uwrite(apiModel)))
  }
}

object Progress {
  sealed trait ProgressMessage
  case class MonitorProgress(id: Long) extends ProgressMessage
  case class StopMonitorProgress(monitorChannel: MonitorChannel)
      extends ProgressMessage
  case class MonitorChannel(id: Long,
                            value: (Iteratee[String, _], Enumerator[String]),
                            channel: Channel[String])
}
