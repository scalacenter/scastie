package `com.olegych.scastie.sclirunner`

import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.ActorLogging
import com.olegych.scastie.util.ActorReconnecting
import com.olegych.scastie.util.ReconnectInfo
import akka.actor.ActorContext
import com.olegych.scastie.util.SbtTask
import com.olegych.scastie.api.SnippetId
import com.olegych.scastie.api.Inputs

// Low level process interation
import scala.sys.process._
import java.nio.charset.StandardCharsets
import scala.io.{Source => IOSource}

object ScliActor {
  // States
  sealed trait ScliState

  case object Available extends ScliState
  case object Running extends ScliState

  case class ScliTask(snippetId: SnippetId, inputs: Inputs, ip: String, login: Option[String], progressActor: ActorRef)

  def sbtTaskToScliTask(sbtTask: SbtTask): ScliTask = {
    sbtTask match {
      case SbtTask(snippetId, inputs, ip, login, progressActor) =>
        ScliTask(snippetId, inputs, ip, login, progressActor)
    }
  }
}


class ScliActor(system: ActorSystem,
               isProduction: Boolean,
               readyRef: Option[ActorRef])
    extends Actor
    with ActorLogging
    with ActorReconnecting {

  import ScliActor._

  // Initial state
  var currentState: ScliState = Available
  override def receive: Receive = whenAvailable


  // FSM

  // Available state (no running scala cli instance)
  val whenAvailable: Receive = message => message match {
    case task @ SbtTask(_, _, _, _, _) => {
      log.warning("Should not receive an SbtTask, converting to ScliTask")
      runTask(sbtTaskToScliTask(task))
    }
    case task @ ScliTask(_, _, _, _, _) => runTask(task)
  }

  // Unavailable state (running scala cli instance)
  val whenRunning: Receive = message => message match {
    // TODO: answer appropriately, maybe queue?
    case any => ()
  }


  // Run task
  def runTask(task: ScliTask): Unit = {
    log.info(s"Running task: $task")
    currentState = Running

    val processBuilder: ProcessBuilder = Process("scala-cli -") // TODO: change to scala scripts!? maybe let the user chose.
    val io = BasicIO.standard(true)
      .withInput(write => {
        write.write(task.inputs.code.getBytes(StandardCharsets.UTF_8))
        write.close()
      })
      .withOutput(output => {
        val outputString = IOSource.fromInputStream(output).mkString
        log.info(s"Output WOOOOOAA: $outputString")
        // TODO: handle correctly lol
      })

    processBuilder.run(io)
  }

  override def reconnectInfo: Option[ReconnectInfo] = None // TODO: talk about it

  override def tryConnect(context: ActorContext): Unit = () // TODO: talk about it

        

}