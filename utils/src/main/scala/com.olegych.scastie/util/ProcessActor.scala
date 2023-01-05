package com.olegych.scastie.util

import java.nio.file._
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

import akka.actor.{Actor, ActorRef, Props, Stash}
import akka.contrib.process._
import akka.stream.scaladsl.{Flow, Framing, Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, OverflowStrategy, ThrottleMode}
import akka.util.ByteString
import com.olegych.scastie.api.{ProcessOutput, ProcessOutputType}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object ProcessActor {
  case class Input(line: String)

  case object Shutdown

  def props(command: List[String],
            workingDir: Path = Paths.get(System.getProperty("user.dir")),
            environment: Map[String, String] = Map.empty,
            killOnExit: Boolean = false): Props = {
    Props(new ProcessActor(command, workingDir, environment, killOnExit))
  }
}

/*
  > Output line type
  < Input line
  ! Exit
 */

class ProcessActor(command: List[String], workingDir: Path, environment: Map[String, String], killOnExit: Boolean)
    extends Actor
    with Stash {

  import ProcessActor._

  private val log = LoggerFactory.getLogger(getClass)

  // private val props =
  //   NonBlockingProcessPkill.props(
  //     command = command,
  //     workingDir = workingDir.toFile,
  //     environment = environment
  //   )
  // import NonBlockingProcess._

  private val props =
    BlockingProcess.props(
      command = command,
      workingDir = workingDir.toFile,
      environment = environment
    )
  import BlockingProcess._

  private val process = context.actorOf(props, name = "process")

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

  private implicit val materializer = ActorMaterializer(
    ActorMaterializerSettings(context.system)
  )

  private val outputId = new AtomicLong(0)
  override def receive: Receive = {
    case Started(pid, stdin, stdout, stderr) =>
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
            context.parent ! output
            now
        })

      val stdin2: Source[ByteString, ActorRef] =
        Source
          .actorRef[Input](Int.MaxValue, OverflowStrategy.fail)
          .map { case Input(line) => ByteString(line + "\n") }

      val ref: ActorRef =
        Flow[ByteString]
          .to(stdin)
          .runWith(stdin2)

      context.become(active(ref))

      unstashAll()

    case input: Input =>
      stash()
  }

  private def active(stdin: ActorRef): Receive = {
    case input: Input =>
      println(s"< ${outputId.incrementAndGet()}: $input")
      stdin ! input

    case Exited(exitValue) =>
      if (killOnExit) {
        throw new Exception("process exited: " + exitValue)
      }
  }
}
