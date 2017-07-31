package com.olegych.scastie.sbtScastie

import com.olegych.scastie.proto

import com.olegych.scastie.api.RuntimeErrorHelper

import com.trueaccord.scalapb.json.{Printer => JsonPbPrinter}

import sbt._
import Keys._

import java.io.{PrintWriter, OutputStream, StringWriter}

private object LinePrinter {
  val jsonPbPrinter = new JsonPbPrinter()

  def apply(): LinePrinter = {
    def out(in: String): Unit = {
      val protoMessage =
        proto.Sbt().withWrapSbtOutput(proto.SbtOutput(in.trim))

      println(jsonPbPrinter.print(protoMessage))
    }

    new LinePrinter(new OutputStream {
      override def write(b: Array[Byte]): Unit = {
        out(new String(b))
      }
      override def write(b: Array[Byte], off: Int, len: Int): Unit = {
        out(new String(b, off, len))
      }
      override def close(): Unit = ()
      override def flush(): Unit = ()
      override def write(b: Int): Unit = ()
    })
  }
}
private class LinePrinter(os: OutputStream) extends PrintWriter(os)

class RuntimeErrorLogger() extends Logger {
  val jsonPbPrinter = new JsonPbPrinter()

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
      RuntimeErrorHelper
        .fromThrowable(t)
        .foreach(
          error =>
            println(
              jsonPbPrinter.print(proto.Sbt().withWrapRuntimeError(error)
            )
          )
        )
    }
  }
}

object RuntimeErrorLogger {
  val settings: Seq[sbt.Def.Setting[_]] = Seq(
    extraLoggers := { (key: ScopedKey[_]) =>
      Seq(FullLogger(new RuntimeErrorLogger()))
    },
    showSuccess := false,
    logManager := LogManager.defaults(
      extraLoggers.value,
      ConsoleOut.printWriterOut(LinePrinter())
    )
  )
}
