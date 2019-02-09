package com.olegych.scastie.sbtscastie

import com.olegych.scastie.api.{ConsoleOutput, ProcessOutputType, ProcessOutput, RuntimeError, RuntimeErrorWrap}

import sbt._
import Keys._

import play.api.libs.json.Json

import java.io.{PrintWriter, OutputStream, StringWriter}

object RuntimeErrorLogger {
  private object NoOp {
    def apply(): NoOp = {
      def out(in: String): Unit = {
        println(
          Json.stringify(
            Json.toJson(
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

  val settings: Seq[sbt.Def.Setting[_]] = Seq(
    extraLoggers := {
      val clientLogger = FullLogger {
        new Logger {
          def log(level: Level.Value, message: => String): Unit = ()

          def success(message: => String): Unit = () // << this is never called

          def trace(t: => Throwable): Unit = {

            // Nonzero exit code: 1
            val sbtTrap =
              t.isInstanceOf[RuntimeException] &&
                t.getMessage == "Nonzero exit code: 1" &&
                !t.getStackTrace.exists(
                  e => e.getClassName == "sbt.Run" && e.getMethodName == "invokeMain"
                )

            if (!sbtTrap) {
              val error = RuntimeErrorWrap(RuntimeError.fromThrowable(t))
              println(Json.stringify(Json.toJson(error)))
            }
          }
        }
      }
      // val currentFunction = extraLoggers.value
      (key: ScopedKey[_]) =>
        Seq(clientLogger)
    },
    showSuccess := false,
    logManager := LogManager.defaults(
      extraLoggers.value,
      ConsoleOut.printWriterOut(NoOp())
    )
  )
}
