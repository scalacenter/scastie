package org.scastie.scalacli

import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorContext
import org.scastie.util.ActorReconnecting
import org.scastie.util.ReconnectInfo
import org.scastie.util.SbtTask
import org.scastie.api._
import org.scastie.scalacli.ScalaCliRunner
import org.scastie.scalacli.BspClient
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure
import scala.util.Success

import java.time.Instant

import scala.sys.process._
import java.nio.charset.StandardCharsets
import scala.io.{Source => IOSource}
import scala.util.control.NonFatal
import akka.actor.ActorSelection
import scala.concurrent.duration.FiniteDuration
import org.scastie.util.ScalaCliActorTask
import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import org.agrona.concurrent.status.AtomicCounter
import java.util.concurrent.atomic.AtomicLong
import java.nio.file.Files
import java.nio.file.Path


class ScalaCliActor(
               isProduction: Boolean,
               runTimeout: FiniteDuration,
               readyRef: Option[ActorRef],
               override val reconnectInfo: Option[ReconnectInfo],
               coloredStackTrace: Boolean = true,
               workingDir: Path = Files.createTempDirectory("scastie"),
               compilationTimeout: FiniteDuration = 30.seconds, // move this to .conf
               runtimeTimeout: FiniteDuration = 30.seconds
      ) extends Actor with ActorLogging with ActorReconnecting {


  private val runner: ScalaCliRunner = new ScalaCliRunner(coloredStackTrace, workingDir)

  override def receive: Receive = reconnectBehavior orElse { message => message match {
    case task: ScalaCliActorTask => runTask(task, sender())
    case RunnerPing => sender() ! RunnerPong
    case _ =>
  }}

  override def tryConnect(context: ActorContext): Unit = {
    if (isProduction) {
      reconnectInfo.foreach { info =>
        import info._
        balancer(context, info) ! RunnerConnect(actorHostname, actorAkkaPort)
      }
    }
  }

  private def makeOutput(str: String) = Some(ProcessOutput(str, tpe = ProcessOutputType.StdOut, id = None))
  private def makeOutput(str: List[String]): Option[ProcessOutput] = makeOutput(str.mkString("\n"))

  // Run task
  private def runTask(task: ScalaCliActorTask, author: ActorRef): Unit = {
    val ScalaCliActorTask(snippetId, inputs, ip, progressActor) = task
    val progressId: AtomicLong = new AtomicLong(0L)

    val onOutput: ProcessOutput => Any = output =>
      sendProgress(progressActor, author, SnippetProgress.default.copy(
          id = Some(progressId.getAndIncrement()),
          ts = Some(Instant.now.toEpochMilli),
          snippetId = Some(snippetId),
          userOutput = Some(output),
          isDone = false
      ))

    runner.runTask(snippetId, inputs, runTimeout, onOutput).map {
      case Right(output) =>
        sendProgress(progressActor, author, SnippetProgress.default.copy(
          id = Some(progressId.getAndIncrement()),
          ts = Some(Instant.now.toEpochMilli),
          snippetId = Some(snippetId),
          isDone = true,
          runtimeError = output.runtimeError,
          buildOutput = makeOutput(s"Process exited with error code ${output.exitCode}"),
          instrumentations = output.instrumentation,
          compilationInfos = output.diagnostics
        ))
      case Left(compilationError: CompilationError) =>
        sendProgress(progressActor, author, SnippetProgress.default.copy(
          id = Some(progressId.getAndIncrement()),
          ts = Some(Instant.now.toEpochMilli),
          snippetId = Some(snippetId),
          compilationInfos = compilationError.diagnostics,
          userOutput = makeOutput(Nil),
          isDone = true
        ))
      case Left(BspTaskTimeout(msg)) =>
        Process("scala-cli --power bloop exit").!
        Process("scala-cli --power bloop start").!
        sendProgress(progressActor, author, buildErrorProgress(snippetId, msg, progressId.getAndIncrement()))
      case Left(error) =>
        sendProgress(progressActor, author, buildErrorProgress(snippetId, error.msg, progressId.getAndIncrement()))
    }.recover {
      case error =>
        log.error(error, "FATAL ERROR")
    }
  }

  private def sendProgress(progressActor: ActorRef, author: ActorRef, snippetProgress: SnippetProgress): Unit = {
    implicit val tm = Timeout(10.seconds)
    progressActor ! snippetProgress
    (author ? snippetProgress)
      .recover {
        case e =>
          log.error(e, s"error while saving progress $snippetProgress")
      }
  }

  private def buildErrorProgress(snippetId: SnippetId, error: String, progressId: Long) = {
    SnippetProgress.default.copy(
      id = Some(progressId),
      ts = Some(Instant.now.toEpochMilli),
      snippetId = Some(snippetId),
      isDone = true,
      compilationInfos = List(Problem(Error, Some(-1), error)),
      buildOutput = Some(ProcessOutput(error, ProcessOutputType.StdErr, None))
    )
  }

  // Reconnection
  def balancer(context: ActorContext, info: ReconnectInfo): ActorSelection = {
    import info._
    context.actorSelection(
      s"akka://Web@$serverHostname:$serverAkkaPort/user/DispatchActor/ScalaCliDispatcher"
    )
  }


}
