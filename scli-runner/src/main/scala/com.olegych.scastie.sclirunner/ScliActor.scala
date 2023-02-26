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
import com.olegych.scastie.api.SbtPing
import com.olegych.scastie.api.SbtPong

// Low level process interation
import scala.sys.process._
import java.nio.charset.StandardCharsets
import scala.io.{Source => IOSource}
import com.olegych.scastie.sclirunner.ScliRunner
import com.olegych.scastie.api.SnippetProgress

object ScliActor {
  // States
  sealed trait ScliState

  case object Available extends ScliState
  case object Running extends ScliState

  case class ScliActorTask(snippetId: SnippetId, inputs: Inputs, ip: String, login: Option[String], progressActor: ActorRef)

  def sbtTaskToScliActorTask(sbtTask: SbtTask): ScliActorTask = {
    sbtTask match {
      case SbtTask(snippetId, inputs, ip, login, progressActor) =>
        ScliActorTask(snippetId, inputs, ip, login, progressActor)
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

  // Runner
  private val runner: ScliRunner = new ScliRunner // TODO: is new normal?

  // Initial state
  var currentState: ScliState = Available
  override def receive: Receive = whenAvailable


  // FSM
  // Available state (no running scala cli instance)
  val whenAvailable: Receive = message => message match {
    case task @ SbtTask(_, _, _, _, _) => {
      log.warning("Should not receive an SbtTask, converting to ScliActorTask")
      runTask(sbtTaskToScliActorTask(task))
    }
    case task @ ScliActorTask(_, _, _, _, _) => runTask(task)

    case SbtPing => sender() ! SbtPong
    case x => log.error(s"CHECK CHECK CHECK URGENT dead letter: $x")
  }

  // Unavailable state (running scala cli instance)
  val whenRunning: Receive = message => message match {
    // TODO: answer appropriately, maybe queue?
    case any => ()
  }


  // Run task
  def runTask(task: ScliActorTask): Unit = {
    val ScliActorTask(snipId, inp, ip, login, progressActor) = task

    // sendProgress(progressActor, SnippetProgress.default.copy(isDone = false, ts = Some(Instant.now.toEpochMilli), snippetId = Some(snipId)))

    // TODO: keep track of progressActor?
    val r = runner.runTask(ScliRunner.ScliTask(snipId, inp, ip, login))
  }

  // Progress
  private var progressId = 0L

  private def sendProgress(progressActor: ActorRef, _p: SnippetProgress): Unit = {
    progressId = progressId + 1
    val p: SnippetProgress = _p.copy(id = Some(progressId))
    progressActor ! p
  }

  override def reconnectInfo: Option[ReconnectInfo] = None // TODO: fill

  override def tryConnect(context: ActorContext): Unit = () // TODO: fill
}