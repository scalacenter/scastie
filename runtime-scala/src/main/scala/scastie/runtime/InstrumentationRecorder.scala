package org.scastie.runtime

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import scala.collection.mutable.ArrayBuffer

import org.scastie.runtime.api._

trait InstrumentationRecorder {

  private val myBinders = ArrayBuffer.empty[Binder]
  val myStatements = ArrayBuffer.empty[Statement]
  private var statementPosition = Position(0, 0)

  object $doc {

    def binder(render: Render, startLine: Int, endLine: Int): Render = {
      val pos = Position(startLine, endLine)
      myBinders.append(Binder(pos, render))
      render
    }

    def startStatement(startLine: Int, endLine: Int): Unit = {
      statementPosition = Position(startLine, endLine)
      myBinders.clear()
    }

    def endStatement(): Unit = {
      myStatements.append(Statement(myBinders.toList, statementPosition))
    }

    def getResults(): List[Statement] = {
      myStatements.toList
    }

  }

}
