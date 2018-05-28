package com.olegych.scastie.util

import com.olegych.scastie.api.{ProcessOutputType, ProcessOutput}

import akka.actor.{Actor, ActorRef, Props, Stash}
import akka.contrib.process._

import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.stream.OverflowStrategy
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Framing
import akka.stream.scaladsl.{Sink, Source, Flow}

import akka.util.ByteString

import scala.concurrent.duration._

import java.nio.file._

import org.slf4j.LoggerFactory

object ProcessActor {
  case class Input(line: String)

  case object Shutdown

  def props(command: List[String],
            workingDir: Path = Paths.get(System.getProperty("user.dir")),
            environment: Map[String, String] = Map.empty,
            killOnExit: Boolean = false): Props = {
    Props(new ProcessActor(command, workingDir, environment, killOnExit))
  }

  val lineSeparator = System.lineSeparator
}

/*
  > Output line type
  < Input line
  ! Exit
 */

class ProcessActor(command: List[String],
                   workingDir: Path,
                   environment: Map[String, String],
                   killOnExit: Boolean)
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

  private case object FlowComplete

  private def lines(std: Source[ByteString, _]): Source[String, _] = {
    std
      .via(
        Framing.delimiter(
          ByteString(lineSeparator),
          maximumFrameLength = 54000,
          allowTruncation = true
        )
      )
      .map(_.utf8String)
  }

  final implicit val materializer = ActorMaterializer(
    ActorMaterializerSettings(context.system)
  )

  override def receive: Receive = {
    case Started(pid, stdin, stdout, stderr) => {
      println("process started: " + pid)
      lines(stdout)
        .map(line => ProcessOutput(line, ProcessOutputType.StdOut))
        .merge(
          lines(stderr)
            .map(line => ProcessOutput(line, ProcessOutputType.StdErr))
        )
        .throttle(100, 1.second, 100, ThrottleMode.Enforcing)
        .runWith(Sink.foreach(context.parent ! _))

      val stdin2: Source[ByteString, ActorRef] =
        Source
          .actorRef[Input](Int.MaxValue, OverflowStrategy.fail)
          .map { case Input(line) => ByteString(line + lineSeparator) }

      val ref: ActorRef =
        Flow[ByteString]
          .to(stdin)
          .runWith(stdin2)

      context.become(active(ref))

      unstashAll()
    }

    case input: Input =>
      stash()
  }

  private def active(stdin: ActorRef): Receive = {
    case input: Input => {
      stdin ! input
    }

    case Exited(exitValue) => {
      if (killOnExit) {
        throw new Exception("process exited: " + exitValue)
      }
    }
  }
}
