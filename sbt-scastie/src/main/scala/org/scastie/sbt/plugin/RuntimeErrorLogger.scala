package sbt.internal.util.org.scastie.sbtplugin

import io.circe._
import io.circe.syntax._
import org.scastie.api._
import org.apache.logging.log4j.core.{Appender => XAppender, LogEvent => XLogEvent}
import org.apache.logging.log4j.message.ObjectMessage
import sbt.Keys._
import sbt._
import sbt.internal.util.ConsoleAppender.Properties
import sbt.internal.util.{ConsoleAppender, Log4JConsoleAppender, ObjectEvent, TraceEvent}

import java.io.{OutputStream, PrintWriter}
import java.nio.channels.ClosedChannelException
import java.util.concurrent.atomic.AtomicReference
import org.scastie.api._
import org.scastie.runtime.api.{Instrumentation, RuntimeErrorWrap, RuntimeError}
import RuntimeCodecs._

object RuntimeErrorLogger {
  private val scastieOut = new PrintWriter(new OutputStream {
    def out(in: String): Unit = {
      val consoleOutput: ConsoleOutput = SbtOutput(ProcessOutput(in.trim, ProcessOutputType.StdOut, None))
      println(consoleOutput.asJson.noSpaces)
    }
    override def write(b: Int): Unit = ()
    override def write(b: Array[Byte]): Unit = out(new String(b))
    override def write(b: Array[Byte], off: Int, len: Int): Unit = out(new String(b, off, len))
    override def close(): Unit = ()
    override def flush(): Unit = ()
  })

  private def findThrowable(event: XLogEvent) = {
    //daaamn
    Option(event.getThrown).orElse {
      for {
        e <- Option(event.getMessage).collect {
          case e: ObjectMessage => e
        }
        e <- Option(e.getParameter).collect {
          case e: ObjectEvent[_] => e
        }
        e <- Option(e.message).collect {
          case e: TraceEvent => e
        }
        //since worksheet wraps the code in object we unwrap it to display clearer message
        e <- Option(e.message).collect {
          case e: ExceptionInInitializerError if e.getCause != null && e.getCause.getStackTrace.headOption.exists { e =>
                e.getClassName == Instrumentation.instrumentedObject + "$" && e.getMethodName == "<clinit>"
              } =>
            e.getCause
          case e => e
        }
      } yield e
    }
  }
  private def logThrowable(throwable: Throwable): Unit = {
    val error = RuntimeErrorWrap(RuntimeError.fromThrowable(throwable))
    println(error.asJson.noSpaces)
  }

  val settings: Seq[sbt.Def.Setting[_]] = Seq(
    showSuccess := false,
    useLog4J := true,
    logManager := sbt.internal.LogManager.withLoggers(
      (_, _) =>
        new ConsoleAppender(ConsoleAppender.generateName, Properties.from(ConsoleOut.printWriterOut(scastieOut), true, false), _ => None) {
          override def trace(t: => Throwable, traceLevel: Int): Unit = logThrowable(t)
          private[this] val log4j = new AtomicReference[XAppender](null)
          private[sbt] override lazy val toLog4J = log4j.get match {
            case null =>
              log4j.synchronized {
                log4j.get match {
                  case null =>
                    val l = new Log4JConsoleAppender(
                      name,
                      properties,
                      suppressedMessage, { event =>
                        val level = ConsoleAppender.toLevel(event.getLevel)
                        val message = event.getMessage
                        findThrowable(event).foreach(logThrowable)
                        try appendMessage(level, message)
                        catch { case _: ClosedChannelException => }
                      }
                    )
                    log4j.set(l)
                    l
                  case l => l
                }
              }
          }
      }
    ),
  )
}
