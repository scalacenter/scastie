package com.olegych.scastie
package balancer

// import api._

import akka.actor.{ActorLogging, Actor}
// import upickle.default.{write => uwrite}

class ProgressActor extends Actor with ActorLogging {
  def receive = {
    case x => println("PROGRESS")
  }
}
//   import Progress._
//   // private val monitors =
//   //   new mutable.HashMap[Int, mutable.Set[MonitorChannel]]
//   //   with mutable.MultiMap[Int, MonitorChannel]

//   // private val progressBuffer = mutable.Map[Int, PasteProgress]()

//   def receive = LoggingReceive {
//     case MonitorProgress(id) => {

//     }
//     case pasteProgress: PasteProgress => {

//     }
//   }

//   //   case MonitorProgress(id) => {
//   //     val (enumerator, channel) = Concurrent.broadcast[String]
//   //     val monitorChannel = MonitorChannel(id, null, channel)
//   //     import concurrent.ExecutionContext.Implicits.global
//   //     val iteratee = Iteratee.ignore[String].map { _ =>
//   //       self ! StopMonitorProgress(monitorChannel)
//   //     }
//   //     monitors.addBinding(id, monitorChannel)
//   //     sender ! monitorChannel.copy(value = iteratee -> enumerator)
//   //     progressBuffer.get(id).foreach(sendProgress)
//   //   }

//   //   case StopMonitorProgress(monitorChannel) => {
//   //     monitors.removeBinding(monitorChannel.id, monitorChannel)
//   //     ()
//   //   }

//   //   case pasteProgress: PasteProgress => {
//   //     sendProgress(pasteProgress)
//   //   }
//   // }

//   // private def sendProgress(pasteProgress: PasteProgress): Unit = {
//   //   val monitorChannels = monitors.get(pasteProgress.id).toList.flatten
//   //   if (monitorChannels.isEmpty) {
//   //     progressBuffer += (pasteProgress.id -> pasteProgress)
//   //   } else {
//   //     progressBuffer.remove(pasteProgress.id)
//   //   }

//   //   monitorChannels.foreach(_.channel.push(uwrite(pasteProgress)))
//   // }
// }

// object Progress {
//   sealed trait ProgressMessage
//   case class MonitorProgress(id: Int) extends ProgressMessage
//   case class StopMonitorProgress(monitorChannel: MonitorChannel)
//       extends ProgressMessage
//   case class MonitorChannel(id: Int,
//                             value: (Iteratee[String, _], Enumerator[String]),
//                             channel: Channel[String])
// }
