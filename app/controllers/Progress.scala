package controllers

import akka.actor.{ActorLogging, Actor}
import play.api.libs.iteratee.{Concurrent, Enumerator, Iteratee}
import play.api.libs.json._
import controllers.Progress.{MonitorChannel, StopMonitorProgress, MonitorProgress}
import collection.mutable
import play.api.libs.iteratee.Concurrent.Channel
import com.olegych.scastie.PastesActor.PasteProgress
import akka.event.LoggingReceive

/**
  */
class Progress extends Actor with ActorLogging {
  val monitors = new
          mutable.HashMap[Long, mutable.Set[MonitorChannel]] with mutable.MultiMap[Long, MonitorChannel]

  def receive = LoggingReceive {
    case MonitorProgress(id) =>
      val (enumerator, channel) = Concurrent.broadcast[JsValue]
      val monitorChannel = MonitorChannel(id, null, channel)
      val iteratee = Iteratee.ignore[JsValue].mapDone { _ =>
        self ! StopMonitorProgress(monitorChannel)
      }
      monitors.addBinding(id, monitorChannel)
      sender ! monitorChannel.copy(value = iteratee -> enumerator)
    case StopMonitorProgress(monitorChannel) =>
      monitors.removeBinding(monitorChannel.id, monitorChannel)
    case pasteProgress: PasteProgress =>
      //required for macros json to work
      import play.api.libs.functional.syntax._
      implicit val format = Json.format[PasteProgress]
      monitors.get(pasteProgress.id).toList.flatten.map(_.channel.push(Json.toJson(pasteProgress)))
  }
}

object Progress {

  sealed trait ProgressMessage

  case class MonitorProgress(id: Long) extends ProgressMessage

  case class StopMonitorProgress(monitorChannel: MonitorChannel) extends ProgressMessage

  case class MonitorChannel(id: Long, value: (Iteratee[JsValue, _], Enumerator[JsValue]),
                            channel: Channel[JsValue])

}
