package com.olegych.scastie.sbtscastie

import com.olegych.scastie.api

import play.api.libs.json.Json

import sbt._
import Keys._
import KeyRanks.DTask

import System.{lineSeparator => nl}

import xsbti.{Reporter, Problem, Position, Severity}
import java.util.Optional

object CompilerReporter {
  // compilerReporter is marked private in sbt
  private lazy val compilerReporter = TaskKey[xsbti.Reporter](
    "compilerReporter",
    "Experimental hook to listen (or send) compilation failure messages.",
    DTask
  )

  val setting: sbt.Def.Setting[_] =
    Compile / compile / compilerReporter := new xsbti.Reporter {
      private val buffer = collection.mutable.ArrayBuffer.empty[Problem]
      def reset(): Unit = buffer.clear()
      def hasErrors: Boolean = buffer.exists(_.severity == Severity.Error)
      def hasWarnings: Boolean = buffer.exists(_.severity == Severity.Warn)

      def printSummary(): Unit = {
        def toApi(p: Problem): api.Problem = {
          def toOption[T](m: Optional[T]): Option[T] = {
            if (!m.isPresent) None
            else Some(m.get)
          }
          val severity =
            p.severity match {
              case xsbti.Severity.Info  => api.Info
              case xsbti.Severity.Warn  => api.Warning
              case xsbti.Severity.Error => api.Error
            }
          api.Problem(severity, toOption(p.position.line).map(_.toInt), p.message)
        }
        if (problems.nonEmpty) {
          val apiProblems = problems.map(toApi)
          println(Json.stringify(Json.toJson(apiProblems)))
        }
      }
      def problems: Array[Problem] = buffer.toArray
//        def log(pos: Position, msg: String, sev: Severity): Unit = {
      def log(problem: Problem): Unit = {
        object MyProblem extends Problem {
          def category: String = "foo"
          def severity: Severity = problem.severity()
          def message: String = problem.message()
          def position: Position = problem.position()
          override def toString = s"$position:$severity: $message"
        }
        buffer.append(MyProblem)
      }
      def comment(pos: xsbti.Position, msg: String): Unit = ()
    }
}
