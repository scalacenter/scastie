package org.scastie.runtime

import org.scastie.runtime.api._

protected[runtime] trait SharedRuntime {

  def write(instrumentations: List[Instrumentation]): String = {
    if (instrumentations.isEmpty) "" else instrumentations.map(_.asJsonString).mkString("[", ",", "]")
  }

  def writeStatements(statements: List[Statement]): String = {
    val instrumentations = statements.flatMap { statement =>
      statement.binders.map { binder =>
        Instrumentation(
          position = binder.pos,
          render = binder.render
        )
      }
    }

    write(instrumentations)
  }

  private val maxValueLength = 500

  private def show[A](a: A): String =
    if (a == null) "null"
    else a.toString

  protected[runtime] def render[T](a: T, typeName: String): Render = {
    a match {
      case html: Html => html
      case v =>
        val vs = show(v)
        val out =
          if (vs.size > maxValueLength) vs.take(maxValueLength) + "..."
          else vs

        Value(out, typeName.replace(Instrumentation.instrumentedObject + ".", ""))
    }
  }
}
