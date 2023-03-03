package `com.olegych.scastie.sclirunner`

import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorContext
import com.olegych.scastie.util.ActorReconnecting
import com.olegych.scastie.util.ReconnectInfo
import com.olegych.scastie.util.SbtTask
import com.olegych.scastie.api.SnippetId
import com.olegych.scastie.api.Inputs
import com.olegych.scastie.api.SbtPing
import com.olegych.scastie.api.SbtPong
import com.olegych.scastie.api.SnippetProgress
import com.olegych.scastie.api.ProcessOutput
import com.olegych.scastie.api.ProcessOutputType
import com.olegych.scastie.sclirunner.ScliRunner
import com.olegych.scastie.sclirunner.BspClient
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure
import scala.util.Success

import java.time.Instant

import scala.sys.process._
import java.nio.charset.StandardCharsets
import scala.io.{Source => IOSource}

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
  private val runner: ScliRunner = new ScliRunner

  // Initial state
  var currentState: ScliState = Available
  override def receive: Receive = whenAvailable


  // FSM
  // Available state (no running scala cli instance)
  val whenAvailable: Receive = reconnectBehavior orElse { message => message match {
    case task @ SbtTask(_, _, _, _, _) => {
      log.warning("Should not receive an SbtTask, converting to ScliActorTask")
      runTask(sbtTaskToScliActorTask(task))
    }
    case task @ ScliActorTask(_, _, _, _, _) => runTask(task)

    case SbtPing => sender() ! SbtPong
    case x => log.error(s"CHECK CHECK CHECK URGENT dead letter: $x")
  } }

  // Unavailable state (running scala cli instance)
  val whenRunning: Receive = reconnectBehavior orElse { message => message match {
    // TODO: answer appropriately, maybe queue?
    case any => ()
  } }


  // Run task
  def runTask(task: ScliActorTask): Unit = {
    val ScliActorTask(snipId, inp, ip, login, progressActor) = task

    // sendProgress(progressActor, SnippetProgress.default.copy(isDone = false, ts = Some(Instant.now.toEpochMilli), snippetId = Some(snipId)))

    // TODO: keep track of progressActor?
    val r = runner.runTask(ScliRunner.ScliTask(snipId, inp, ip, login), output => {
      sendProgress(progressActor, SnippetProgress.default.copy(
        ts = Some(Instant.now.toEpochMilli),
        snippetId = Some(snipId),
        userOutput = Some(ProcessOutput(output, tpe = ProcessOutputType.StdOut, id = None)),
        isDone = false
      ))
    })

    r.onComplete({
      case Failure(exception) => {
        println(s"except $exception")
        exception match {
          case ScliRunner.InstrumentationException(report) => sendProgress(progressActor, report.toProgress(snippetId = snipId))
          // case x: BspClient.NoTargetsFoundException => sendProgress(progressActor, buildErrorProgress(snipId, x.err)
          case x @ ScliRunner.CompilationError(problems) => {
            sendProgress(progressActor, SnippetProgress.default.copy(
              ts = Some(Instant.now.toEpochMilli),
              snippetId = Some(snipId),
              compilationInfos = problems,
              isSbtError = false,
              isDone = true
            ))
          }
        }
      }
      case Success(value) => sendProgress(progressActor, SnippetProgress.default.copy(
        ts = Some(Instant.now.toEpochMilli),
        snippetId = Some(snipId),
        isDone = true
      ))
    })
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