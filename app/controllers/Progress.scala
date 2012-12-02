package controllers

import akka.actor.Actor
import play.api.libs.iteratee.{Concurrent, Enumerator, Iteratee}
import play.api.libs.json.{JsString, JsValue}
import controllers.Progress.{MonitorChannel, StopMonitorProgress, MonitorProgress}
import collection.mutable
import play.api.libs.iteratee.Concurrent.Channel
import com.olegych.scastie.PastesActor.Paste

/**
  */
class Progress extends Actor {
  val monitors = new
          mutable.HashMap[Long, mutable.Set[MonitorChannel]] with mutable.MultiMap[Long, MonitorChannel]

  def receive = {
    case MonitorProgress(id) =>
      val (enumerator, channel) = Concurrent.broadcast[JsValue]
      val monitorChannel = MonitorChannel(id, null, channel)
      val iteratee = Iteratee.ignore[JsValue].mapDone(_ => self ! StopMonitorProgress(monitorChannel))
      monitors.addBinding(id, monitorChannel)
      sender ! monitorChannel.copy(value = iteratee -> enumerator)
    case StopMonitorProgress(monitorChannel) =>
      monitors.removeBinding(monitorChannel.id, monitorChannel)
    case paste: Paste =>
      monitors.get(paste.id).toList.flatten.map(_.channel.push(JsString(paste.output.get)))
  }
}

object Progress {

  sealed trait ProgressMessage

  case class MonitorProgress(id: Long) extends ProgressMessage

  case class StopMonitorProgress(monitorChannel: MonitorChannel) extends ProgressMessage

  case class MonitorChannel(id: Long, value: (Iteratee[JsValue, _], Enumerator[JsValue]),
                            channel: Channel[JsValue])

}
