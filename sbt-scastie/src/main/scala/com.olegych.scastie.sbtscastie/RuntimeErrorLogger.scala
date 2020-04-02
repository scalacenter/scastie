package com.olegych.scastie.sbtscastie

import java.io.{OutputStream, PrintWriter}

import com.olegych.scastie.api._
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.layout.PatternLayout
import org.apache.logging.log4j.message.ObjectMessage
import play.api.libs.json.Json
import sbt.Keys._
import sbt._
import sbt.internal.LogManager.suppressedMessage
import sbt.internal.util.MainAppender.defaultScreen
import sbt.internal.util.{ObjectEvent, TraceEvent}

object RuntimeErrorLogger {
  private object NoOp {
    def apply(): NoOp = {
      def out(in: String): Unit = {
        println(
          Json.stringify(
            Json.toJson[ConsoleOutput](
              ConsoleOutput.SbtOutput(ProcessOutput(in.trim, ProcessOutputType.StdOut, None))
            )
          )
        )
      }

      new NoOp(new OutputStream {
        override def close(): Unit = ()
        override def flush(): Unit = ()
        override def write(b: Array[Byte]): Unit =
          out(new String(b))
        override def write(b: Array[Byte], off: Int, len: Int): Unit =
          out(new String(b, off, len))
        override def write(b: Int): Unit = ()
      })
    }
  }
  private class NoOp(os: OutputStream) extends PrintWriter(os)

  private val clientLogger = new AbstractAppender("sbt-scastie-appender", null, PatternLayout.createDefaultLayout(), true, Array()) {
    def append(event: LogEvent): Unit = {
      //daaamn
      val throwable = Option(event.getThrown).orElse {
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
      throwable.foreach { throwable =>
        val error = RuntimeErrorWrap(RuntimeError.fromThrowable(throwable))
        println(Json.stringify(Json.toJson(error)))
      }
    }
    start()
  }

  val settings: Seq[sbt.Def.Setting[_]] = Seq(
    extraLoggers := { (key: ScopedKey[_]) =>
      Seq(clientLogger)
    },
    showSuccess := false,
    logManager := sbt.internal.LogManager.withLoggers(
      (task, state) => defaultScreen(ConsoleOut.printWriterOut(NoOp()), suppressedMessage(task, state)),
      relay = _ => clientLogger
    )
  )
}
