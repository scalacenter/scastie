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
import com.olegych.scastie.api.Problem
import com.olegych.scastie.api
import play.api.libs.json.Reads
import play.api.libs.json.Json
import scala.util.control.NonFatal

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
    case task: SbtTask => {
      log.warning("Should not receive an SbtTask, converting to ScliActorTask")
      runTask(sbtTaskToScliActorTask(task))
    }
    case task: ScliActorTask => runTask(task)

    case SbtPing => sender() ! SbtPong
    case x => log.error(s"CHECK CHECK CHECK URGENT dead letter: $x")
  } }

  // Unavailable state (running scala cli instance)
  val whenRunning: Receive = reconnectBehavior orElse { message => message match {
    // TODO: answer appropriately, maybe queue?
    case any => ()
  } }

  def makeOutput(str: String) = Some(ProcessOutput(str, tpe = ProcessOutputType.StdOut, id = None))
  def makeOutput(str: List[String]): Option[ProcessOutput] = makeOutput(str.mkString("\n"))

  // Run task
  def runTask(task: ScliActorTask): Unit = {
    val ScliActorTask(snipId, inp, ip, login, progressActor) = task

    val r = runner.runTask(ScliRunner.ScliTask(snipId, inp, ip, login), output => {
      sendProgress(progressActor, SnippetProgress.default.copy(
          ts = Some(Instant.now.toEpochMilli),
          snippetId = Some(snipId),
          userOutput = makeOutput(output),
          isDone = false
      ))
    })

    r.onComplete({
      case Failure(exception) => {
        // Unexpected exception
        sendProgress(progressActor, buildErrorProgress(snipId, s"Unexpected exception while running $exception"))
      }
      case Success(Right(error)) => error match {
        // TODO: handle every possible exception
        case ScliRunner.InstrumentationException(report) => sendProgress(progressActor, report.toProgress(snippetId = snipId))
        case ScliRunner.ErrorFromBsp(x: BspClient.NoTargetsFoundException, logs) => sendProgress(progressActor, buildErrorProgress(snipId, x.err, logs))
        case ScliRunner.ErrorFromBsp(x: BspClient.NoMainClassFound, logs) => sendProgress(progressActor, buildErrorProgress(snipId, x.err, logs))
        case ScliRunner.ErrorFromBsp(x: BspClient.FailedRunError, logs) => sendProgress(progressActor, buildErrorProgress(snipId, x.err, logs))
        case ScliRunner.CompilationError(problems, logs) => {
          sendProgress(progressActor, SnippetProgress.default.copy(
            ts = Some(Instant.now.toEpochMilli),
            snippetId = Some(snipId),
            compilationInfos = problems,
            userOutput = makeOutput(logs),
            isDone = true
          ))
        }
      }
      case Success(Left(value)) => sendProgress(progressActor, SnippetProgress.default.copy(
        ts = Some(Instant.now.toEpochMilli),
        snippetId = Some(snipId),
        isDone = true,
        instrumentations = value.instrumentation.getOrElse(List()),
        compilationInfos = value.diagnostics
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

  private def buildErrorProgress(snipId: SnippetId, err: String, logs: List[String] = List()) = {
    SnippetProgress.default.copy(
      ts = Some(Instant.now.toEpochMilli),
      snippetId = Some(snipId),
      isDone = true,
      compilationInfos = List(Problem(api.Error, line = None, message = err)),
      userOutput = Some(ProcessOutput(logs.mkString("\n") + "\n" + err, ProcessOutputType.StdErr, None))
    )
  }

  override def reconnectInfo: Option[ReconnectInfo] = None // TODO: fill

  override def tryConnect(context: ActorContext): Unit = () // TODO: fill

}