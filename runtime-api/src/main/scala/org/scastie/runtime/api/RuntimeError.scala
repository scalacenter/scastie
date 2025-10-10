package org.scastie.runtime.api

import java.io.{PrintWriter, StringWriter}
import StringUtils._

case class RuntimeError(
    message: String,
    line: Option[Int],
    fullStack: String
) {
  def asJsonString: String = {
    s"""{"message":"${message.escaped}","line":${line.getOrElse(null)},"fullStack":"${fullStack.escaped}"}"""
  }
}

object RuntimeError {

  def fromJsonString(json: String): Option[RuntimeError] = {
    val msgPattern   = """"message":"(.*?)"""".r
    val linePattern  = """"line":(null|\d+)""".r
    val stackPattern = """"fullStack":"(.*?)"""".r

    for {
      msgMatch   <- msgPattern.findFirstMatchIn(json)
      stackMatch <- stackPattern.findFirstMatchIn(json)
      lineMatch  <- linePattern.findFirstMatchIn(json)
      msg   = msgMatch.group(1)
      stack = stackMatch.group(1)
      line  = lineMatch.group(1) match {
        case "null" => None
        case numStr => Some(numStr.toInt)
      }
    } yield RuntimeError(msg, line, stack)
  }

  def wrap[T](in: => T): Either[Option[RuntimeError], T] = {
    try {
      Right(in)
    } catch {
      case ex: Exception =>
        Left(RuntimeError.fromThrowable(ex, fromScala = false))
    }
  }

  def fromThrowable(t: Throwable, fromScala: Boolean = true): Option[RuntimeError] = {
    def search(e: Throwable) = {
      e.getStackTrace
        .find(
          trace =>
            if (fromScala)
              trace.getFileName == "main.scala" && trace.getLineNumber != -1
            else true
        )
        .map(v => (e, Some(v.getLineNumber)))
    }
    def loop(e: Throwable): Option[(Throwable, Option[Int])] = {
      val s = search(e)
      if (s.isEmpty)
        if (e.getCause != null) loop(e.getCause)
        else Some((e, None))
      else s
    }

    loop(t).map {
      case (err, line) =>
        val errors = new StringWriter()
        t.printStackTrace(new PrintWriter(errors))
        val fullStack = errors.toString

        RuntimeError(err.toString, line, fullStack)
    }
  }
}

case class RuntimeErrorWrap(error: Option[RuntimeError])
