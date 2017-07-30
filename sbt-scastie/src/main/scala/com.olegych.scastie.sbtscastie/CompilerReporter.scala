package com.olegych.scastie.sbtScastie

import com.olegych.scastie.proto

import com.trueaccord.scalapb.json.{Printer => JsonPbPrinter}

import sbt._
import Keys._
import KeyRanks.DTask

import System.{lineSeparator => nl}

import xsbti.{Reporter, Problem, Position, Severity, Maybe}

class CompilerReporter() extends xsbti.Reporter {
  val jsonPbPrinter = new JsonPbPrinter()

  private val buffer = collection.mutable.ArrayBuffer.empty[Problem]
  def reset(): Unit = buffer.clear()
  def hasErrors: Boolean = buffer.exists(_.severity == Severity.Error)
  def hasWarnings: Boolean = buffer.exists(_.severity == Severity.Warn)

  def printSummary(): Unit = {
    def toProtobuf(p: Problem): proto.Problem = {
      def toOption[T](m: Maybe[T]): Option[T] = {
        if (m.isEmpty) None
        else Some(m.get)
      }
      val severity =
        p.severity match {
          case xsbti.Severity.Info  => proto.Severity.Info
          case xsbti.Severity.Warn  => proto.Severity.Warning
          case xsbti.Severity.Error => proto.Severity.Error
        }
      proto.Problem(
        severity = severity,
        line = toOption(p.position.line).map(_.toInt),
        message = p.message
      )
    }

    if (problems.nonEmpty) {
      val report = proto.CompilationReport(
        problems = problems.map(toProtobuf)
      )

      println(jsonPbPrinter.print(report))
    }
  }
  def problems: Array[Problem] = buffer.toArray
  def log(pos: Position, msg: String, sev: Severity): Unit = {
    object MyProblem extends Problem {
      def category: String = "foo"
      def severity: Severity = sev
      def message: String = msg
      def position: Position = pos
      override def toString = s"$position:$severity: $message"
    }
    buffer.append(MyProblem)
  }
  def comment(pos: xsbti.Position, msg: String): Unit = ()
}

object CompilerReporter {
  // compilerReporter is marked private in sbt
  private lazy val compilerReporter = TaskKey[Option[xsbti.Reporter]](
    "compilerReporter",
    "Experimental hook to listen (or send) compilation failure messages.",
    DTask
  )

  val setting: sbt.Def.Setting[_] =
    compilerReporter in (Compile, compile) := Some(new CompilerReporter())
}