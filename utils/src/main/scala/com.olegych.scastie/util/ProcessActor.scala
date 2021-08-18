package com.olegych.scastie.util

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.contrib.process.BlockingProcess.{Exited, Started}

import java.nio.file._
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import akka.contrib.process._
import akka.stream.scaladsl.{Flow, Framing, Sink, Source}
import akka.stream.typed.scaladsl.ActorSource
import akka.stream.{Materializer, OverflowStrategy, ThrottleMode}
import akka.util.ByteString
import com.olegych.scastie.api.{ProcessOutput, ProcessOutputType}

import scala.concurrent.duration._

object ProcessActor {
  sealed trait Message

  case class ProcessResponse(response: BlockingProcess.Response) extends Message

  case class Input(line: String) extends Message

  def apply(
    replyTo: ActorRef[ProcessOutput],
    command: List[String],
    workingDir: Path = Paths.get(System.getProperty("user.dir")),
    environment: Map[String, String] = Map.empty,
    killOnExit: Boolean = false
  ): Behavior[Message] =
    Behaviors.setup { context =>
      Behaviors.withStash(100) { buffer =>
        new ProcessActor(
          replyTo, command, workingDir, environment, killOnExit
        )(context, buffer).receive
      }
    }
}

import ProcessActor._

/*
  > Output line type
  < Input line
  ! Exit
 */

class ProcessActor private(
  replyTo: ActorRef[ProcessOutput],
  command: List[String],
  workingDir: Path,
  environment: Map[String, String],
  killOnExit: Boolean
)(context: ActorContext[Message], buffer: StashBuffer[Message]) {
  import context.log

  context.spawn(
    BlockingProcess(
      command,
      workingDir = workingDir.toFile,
      environment,
      context.messageAdapter(ProcessResponse)
    ),
    name = "process"
  )

  private def lines(std: Source[ByteString, _]): Source[String, _] = {
    std
      .via(
        Framing.delimiter(
          ByteString("\n"),
          maximumFrameLength = 54000,
          allowTruncation = true
        )
      )
      .map(_.utf8String)
  }

  // https://doc.akka.io/docs/akka/current/stream/stream-flows-and-basics.html#actor-materializer-lifecycle
  implicit val mat: Materializer = Materializer(context)

  private val outputId = new AtomicLong(0)

  def receive: Behavior[Message] = Behaviors.receiveMessage {
    case ProcessResponse(Started(pid, stdin, stdout, stderr)) =>
      println("process started: " + pid)
      lines(stdout)
        .map { line =>
          ProcessOutput(line.stripLineEnd, ProcessOutputType.StdOut, Some(outputId.incrementAndGet()))
        }
        .merge(
          lines(stderr)
            .map { line =>
              ProcessOutput(line.stripLineEnd, ProcessOutputType.StdErr, Some(outputId.incrementAndGet()))
            }
        )
        .throttle(100, 1.second, 100, ThrottleMode.Shaping)
        .runWith(Sink.fold(Instant.now) {
          case (ts, output) =>
            val now = Instant.now
            println(s"> ${output.id.getOrElse(0)} ${now.toEpochMilli - ts.toEpochMilli}ms: ${output.line}")
            replyTo ! output
            now
        })

      val stdin2: Source[ByteString, ActorRef[Input]] =
        ActorSource
          .actorRef[Input](
            // only completed/ failed when this ProcessActor stop
            completionMatcher = PartialFunction.empty,
            failureMatcher = PartialFunction.empty,
            Int.MaxValue,
            OverflowStrategy.fail
          )
          .map { case Input(line) => ByteString(line + "\n") }

      val ref: ActorRef[Input] =
        Flow[ByteString]
          .to(stdin)
          .runWith(stdin2)

      buffer.unstashAll(active(ref))

    case input: Input =>
      buffer.stash(input)
      Behaviors.same

    case x @ ProcessResponse(Exited(_)) =>
      log.error("Unexpected message {}", x)
      Behaviors.same
  }

  private def active(stdin: ActorRef[Input]): Behavior[Message] = Behaviors.receiveMessage {
    case input: Input =>
      println(s"< ${outputId.incrementAndGet()}: $input")
      stdin ! input
      Behaviors.same

    case ProcessResponse(Exited(exitValue)) =>
      if (killOnExit) {
        throw new Exception("process exited: " + exitValue)
      }
      Behaviors.same

    case x @ ProcessResponse(_: Started) =>
      log.error("Unexpected message {}", x)
      Behaviors.same
  }
}
