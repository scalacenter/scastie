package com.olegych.scastie
package sbtscastie

import sbt._
import Keys._
import KeyRanks.DTask

import System.{lineSeparator => nl}

import xsbti.{Reporter, Problem, Position, Severity, Maybe}
import upickle.default.{write => uwrite}

object CompilerReporter {
  // compilerReporter is marked private in sbt
  private lazy val compilerReporter = TaskKey[Option[xsbti.Reporter]](
    "compilerReporter",
    "Experimental hook to listen (or send) compilation failure messages.",
    DTask
  )

  val setting: sbt.Def.Setting[_] =
    compilerReporter in (Compile, compile) := Some(
      new xsbti.Reporter {
        private val buffer = collection.mutable.ArrayBuffer.empty[Problem]
        def reset(): Unit = buffer.clear()
        def hasErrors: Boolean = buffer.exists(_.severity == Severity.Error)
        def hasWarnings: Boolean = buffer.exists(_.severity == Severity.Warn)

        def annoying(in: Problem): Boolean = {
          in.severity == xsbti.Severity.Warn &&
          in.message == "a pure expression does nothing in statement position; you may be omitting necessary parentheses"
        }

        def printSummary(): Unit = {
          def toApi(p: Problem): api.Problem = {
            def toOption[T](m: Maybe[T]): Option[T] = {
              if (m.isEmpty) None
              else Some(m.get)
            }
            val severity =
              p.severity match {
                case xsbti.Severity.Info => api.Info
                case xsbti.Severity.Warn => api.Warning
                case xsbti.Severity.Error => api.Error
              }
            api.Problem(severity,
                        toOption(p.position.line).map(_.toInt),
                        p.message)
          }
          if (problems.nonEmpty) {
            println(uwrite(problems.filterNot(annoying).map(toApi)))
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
    )
}
