/**
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.contrib.process

import akka.actor.{Actor, ActorLogging, ActorRef, NoSerializationVerificationNeeded, Props, SupervisorStrategy, Terminated}
import akka.stream.{ActorAttributes, IOResult}
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import akka.util.{ByteString, Helpers}
import java.io.File
import java.lang.{Process => JavaProcess, ProcessBuilder => JavaProcessBuilder}
import java.util.concurrent.TimeUnit

import scala.collection.immutable
import scala.concurrent.{Future, blocking}
import scala.concurrent.duration.Duration

object BlockingProcess {

  def getPid(process: JavaProcess): Option[Int] = {
    if (Helpers.isWindows) {
      None
    } else {
      val pidField = process.getClass.getDeclaredField("pid")
      pidField.setAccessible(true)
      Some(pidField.get(process).asInstanceOf[Int])
    }
  }

  /**
   * The configuration key to use in order to override the dispatcher used for blocking IO.
   */
  final val BlockingIODispatcherId =
    "akka.process.blocking-process.blocking-io-dispatcher-id"

  /**
   * Sent to the receiver on startup - specifies the streams used for managing input, output and error respectively.
   * This message should only be received by the parent of the BlockingProcess and should not be passed across the
   * JVM boundary (the publishers are not serializable).
   *
   * @param stdin a `akka.stream.scaladsl.Sink[ByteString, Future[IOResult]]` for the standard input stream of the process
   * @param stdout a `akka.stream.scaladsl.Source[ByteString, Future[IOResult]]` for the standard output stream of the process
   * @param stderr a `akka.stream.scaladsl.Source[ByteString, Future[IOResult]]` for the standard error stream of the process
   */
  case class Started(pid: Option[Int],
                     stdin: Sink[ByteString, Future[IOResult]],
                     stdout: Source[ByteString, Future[IOResult]],
                     stderr: Source[ByteString, Future[IOResult]])
      extends NoSerializationVerificationNeeded

  /**
   * Sent to the receiver after the process has exited.
   *
   * @param exitValue the exit value of the process
   */
  case class Exited(exitValue: Int)

  /**
   * Send a request to destroy the process.
   * On POSIX, this sends a SIGTERM, but implementation is platform specific.
   */
  case object Destroy

  /**
   * Send a request to forcibly destroy the process.
   * On POSIX, this sends a SIGKILL, but implementation is platform specific.
   */
  case object DestroyForcibly

  /**
   * Sent if stdin from the process is terminated
   */
  case object StdinTerminated

  /**
   * Sent if stdout from the process is terminated
   */
  case object StdoutTerminated

  /**
   * Sent if stderr from the process is terminated
   */
  case object StderrTerminated

  /**
   * Create Props for a [[BlockingProcess]] actor.
   *
   * @param command signifies the program to be executed and its optional arguments
   * @param workingDir the working directory for the process; default is the current working directory
   * @param environment the environment for the process; default is `Map.emtpy`
   * @param stdioTimeout the amount of time to tolerate waiting for a process to communicate back to this actor
   * @return Props for a [[BlockingProcess]] actor
   */
  def props(command: immutable.Seq[String],
            workingDir: File = new File(System.getProperty("user.dir")),
            environment: Map[String, String] = Map.empty,
            stdioTimeout: Duration = Duration.Undefined) =
    Props(new BlockingProcess(command, workingDir, environment, stdioTimeout))

  private def prepareCommand(command: Seq[String]) =
    if (Helpers.isWindows) List("cmd", "/c") ++ (command map winQuote)
    else command

  /**
   * This quoting functionality is as recommended per http://bugs.java.com/view_bug.do?bug_id=6511002
   * The JDK can't change due to its backward compatibility requirements, but we have no such constraint
   * here. Args should be able to be expressed consistently by the user of our API no matter whether
   * execution is on Windows or not.
   *
   * @param s command string to be quoted
   * @return quoted string
   */
  private def winQuote(s: String): String = {
    def needsQuoting(s: String) =
      s.isEmpty || (s exists (
          c => c == ' ' || c == '\t' || c == '\\' || c == '"'
      ))
    if (needsQuoting(s)) {
      val quoted = s
        .replaceAll("""([\\]*)"""", """$1$1\\"""")
        .replaceAll("""([\\]*)\z""", "$1$1")
      s""""$quoted""""
    } else
      s
  }
}

/**
 * This actor uses the JDK process API. As such, more memory given that more threads are consumed. Favor the
 * [[NonBlockingProcess]] actor unless you *need* to use the JDK.
 *
 * BlockingProcess encapsulates an operating system process and its ability to be communicated with via stdio i.e.
 * stdin, stdout and stderr. The reactive streams for stdio are communicated in a BlockingProcess.Started event
 * upon the actor being established. The parent actor is then subsequently streamed
 * stdout and stderr events. When the process exists (determined by periodically polling process.isAlive()) then
 * the process's exit code is communicated to the receiver in a BlockingProcess.Exited event.
 *
 * A dispatcher as indicated by the "akka.process.blocking-process.blocking-io-dispatcher-id" setting is used
 * internally by the actor as various JDK calls are made which can block.
 */
class BlockingProcess(command: immutable.Seq[String], directory: File, environment: Map[String, String], stdioTimeout: Duration)
    extends Actor
    with ActorLogging {

  import BlockingProcess._
  import context.dispatcher

  override val supervisorStrategy: SupervisorStrategy =
    SupervisorStrategy.stoppingStrategy

  override def preStart(): Unit = {
    println("preStart")
    val process: JavaProcess = {
      import scala.jdk.CollectionConverters._
      val preparedCommand = prepareCommand(command)
      val pb = new JavaProcessBuilder(preparedCommand.asJava)
      pb.environment().putAll(environment.asJava)
      pb.directory(directory)
      pb.start()
    }

    val blockingIODispatcherId =
      context.system.settings.config.getString(BlockingIODispatcherId)

    try {
      val selfDispatcherAttribute =
        ActorAttributes.dispatcher(blockingIODispatcherId)

      val stdin = StreamConverters
        .fromOutputStream(() => process.getOutputStream(), autoFlush = true)
        .withAttributes(selfDispatcherAttribute)
        .mapMaterializedValue(_.andThen { case _ => self ! StdinTerminated })

      val stdout = StreamConverters
        .fromInputStream(() => process.getInputStream())
        .withAttributes(selfDispatcherAttribute)
        .mapMaterializedValue(_.andThen { case _ => self ! StdoutTerminated })

      val stderr = StreamConverters
        .fromInputStream(() => process.getErrorStream())
        .withAttributes(selfDispatcherAttribute)
        .mapMaterializedValue(_.andThen { case _ => self ! StderrTerminated })

      context.parent ! Started(getPid(process), stdin, stdout, stderr)

      log.debug(
        s"Blocking process started with dispatcher: $blockingIODispatcherId"
      )

    } finally {
      context.watch(
        context.actorOf(
          ProcessDestroyer
            .props(process, context.parent)
            .withDispatcher(blockingIODispatcherId),
          "process-destroyer"
        )
      )
    }
  }

  override def receive: Receive = {
    case Destroy =>
      log.debug("Received request to destroy the process.")
      tellDestroyer(ProcessDestroyer.Destroy)
    case DestroyForcibly =>
      log.debug("Received request to forcibly destroy the process.")
      tellDestroyer(ProcessDestroyer.DestroyForcibly)
    case Terminated(_) =>
      context.stop(self)
    case StdinTerminated =>
      log.debug("Stdin was terminated")
      tellDestroyer(ProcessDestroyer.Inspect)
    case StdoutTerminated =>
      log.debug("Stdout was terminated")
      tellDestroyer(ProcessDestroyer.Inspect)
    case StderrTerminated =>
      log.debug("Stderr was terminated")
      tellDestroyer(ProcessDestroyer.Inspect)
  }

  private def tellDestroyer(msg: Any) =
    context.child("process-destroyer").foreach(_ ! msg)
}

private object ProcessDestroyer {

  /**
   * The configuration key to use for the inspection interval.
   */
  final val InspectionInterval =
    "akka.process.blocking-process.inspection-interval"

  /**
   * Inspect the Process to ensure it is still alive. This is necessary because
   * a process can exit without its stdout/stderr file handles being closed, for
   * instance if a process forks and a child continues to run when it dies,
   * it will have a reference to those handles.
   */
  case object Inspect

  /**
   * Request that process.destroy() be called
   */
  case object Destroy

  /**
   * Request that process.destroyForcibly() be called
   */
  case object DestroyForcibly

  def props(process: JavaProcess, exitValueReceiver: ActorRef): Props =
    Props(new ProcessDestroyer(process, exitValueReceiver))
}

private class ProcessDestroyer(process: JavaProcess, exitValueReceiver: ActorRef) extends Actor with ActorLogging {
  import ProcessDestroyer._
  import context.dispatcher

  private val inspectionInterval =
    Duration(
      context.system.settings.config.getDuration(InspectionInterval).toMillis,
      TimeUnit.MILLISECONDS
    )

  private val inspectionTick =
    context.system.scheduler.schedule(inspectionInterval, inspectionInterval, self, Inspect)

  def pkill(): Unit = {
    if (Helpers.isWindows) {
      process.destroy()
    } else {
      val pid = BlockingProcess.getPid(process).get
      import sys.process._
      s"pkill -KILL -P $pid".! == 0
    }
  }

  override def receive = {
    case Destroy =>
      blocking(process.destroy())
    case DestroyForcibly =>
      blocking(process.destroyForcibly())
    case Inspect =>
      if (!process.isAlive) {
        log.debug("Process has terminated, stopping self")
        context.stop(self)
      }
  }

  override def postStop(): Unit = {
    inspectionTick.cancel()
    pkill()

    val exitValue = blocking {
      process.destroy()
      process.destroyForcibly()
      process.waitFor()
    }
    exitValueReceiver ! BlockingProcess.Exited(exitValue)
  }
}
