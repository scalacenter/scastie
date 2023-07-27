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
import com.olegych.scastie.api.RunnerPing
import com.olegych.scastie.api.RunnerPong
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
import akka.actor.ActorSelection
import scala.concurrent.duration.FiniteDuration
import com.olegych.scastie.util.ScliActorTask
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask

object ScliActor {
  // States
  sealed trait ScliState

  case object Available extends ScliState
  case object Running extends ScliState

  def sbtTaskToScliActorTask(sbtTask: SbtTask): ScliActorTask = {
    sbtTask match {
      case SbtTask(snippetId, inputs, ip, _, progressActor) =>
        ScliActorTask(snippetId, inputs, ip, progressActor)
    }
  }
}


class ScliActor(system: ActorSystem,
               isProduction: Boolean,
               runTimeout: FiniteDuration,
               readyRef: Option[ActorRef],
               override val reconnectInfo: Option[ReconnectInfo])
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
      // log.warning("Should not receive an SbtTask, converting to ScliActorTask")
      runTask(sbtTaskToScliActorTask(task), sender())
    }
    case task: ScliActorTask => runTask(task, sender())

    case RunnerPing => sender() ! RunnerPong
    case x => log.error(s"CHECK CHECK CHECK URGENT dead letter: $x")
  } }


  def makeOutput(str: String) = Some(ProcessOutput(str, tpe = ProcessOutputType.StdOut, id = None))
  def makeOutput(str: List[String]): Option[ProcessOutput] = makeOutput(str.mkString("\n"))

  // Run task
  def runTask(task: ScliActorTask, author: ActorRef): Unit = {
    val ScliActorTask(snipId, inp, ip, progressActor) = task

    val r = runner.runTask(ScliRunner.ScliTask(snipId, inp, ip), runTimeout, output => {
      sendProgress(progressActor, author, SnippetProgress.default.copy(
          ts = Some(Instant.now.toEpochMilli),
          snippetId = Some(snipId),
          userOutput = makeOutput(output),
          isDone = false
      ))
    })

    r.onComplete({
      case Failure(exception) => {
        // Unexpected exception
        log.error(exception, s"Could not run $snipId")
        sendProgress(progressActor, author, buildErrorProgress(snipId, s"Unexpected exception while running: $exception"))
      }
      case Success(Right(error)) => error match {
        // TODO: handle every possible exception
        case ScliRunner.InstrumentationException(report) => sendProgress(progressActor, author, report.toProgress(snippetId = snipId))
        case ScliRunner.ErrorFromBsp(x: BspClient.NoTargetsFoundException, logs) => sendProgress(progressActor, author, buildErrorProgress(snipId, x.err, logs))
        case ScliRunner.ErrorFromBsp(x: BspClient.NoMainClassFound, logs) => sendProgress(progressActor, author, buildErrorProgress(snipId, x.err, logs))
        case ScliRunner.ErrorFromBsp(x: BspClient.FailedRunError, logs) => sendProgress(progressActor, author, buildErrorProgress(snipId, x.err, logs))
        case ScliRunner.CompilationError(problems, logs) => {
          sendProgress(progressActor, author, SnippetProgress.default.copy(
            ts = Some(Instant.now.toEpochMilli),
            snippetId = Some(snipId),
            compilationInfos = problems,
            userOutput = makeOutput(logs),
            isDone = true
          ))
        }
      }
      case Success(Left(value)) => sendProgress(progressActor, author, SnippetProgress.default.copy(
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

  private def sendProgress(progressActor: ActorRef, author: ActorRef, _p: SnippetProgress): Unit = {
    progressId = progressId + 1
    val p: SnippetProgress = _p.copy(id = Some(progressId))
    progressActor ! p
      implicit val tm = Timeout(10.seconds)
    (author ? p)
      .recover {
        case e =>
          log.error(e, s"error while saving progress $p")
      }
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


  // Reconnection
  def balancer(context: ActorContext, info: ReconnectInfo): ActorSelection = {
    import info._
    context.actorSelection(
      s"akka://Web@$serverHostname:$serverAkkaPort/user/DispatchActor/ScliDispatcher"
    )
  }

  override def tryConnect(context: ActorContext): Unit = {
    if (isProduction) {
      reconnectInfo.foreach { info =>
        import info._
        balancer(context, info) ! api.RunnerConnect(actorHostname, actorAkkaPort)
      }
    }
  }

}