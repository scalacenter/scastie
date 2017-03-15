package com.olegych.scastie
package client

import scala.scalajs.js

import org.scalajs.dom.raw.HTMLElement

import api._
import japgolly.scalajs.react._

import upickle.default.{read => uread}

object Global {
  type Scope = BackendScope[AppProps, AppState]

  private var scope0: Option[Scope] = _

  def subsribe(scope: Scope): Unit = {
    scope0 = Some(scope)
  }

  def signal(instrumentationsRaw: String,
             attachedDoms: js.Array[HTMLElement]): Unit = {
    scope0.foreach { scope =>
      val direct = scope.accessDirect
      val result = uread[Either[Option[RuntimeError], List[Instrumentation]]](
        instrumentationsRaw
      )

      val (instr, runtimeError) = result match {
        case Left(maybeRuntimeError) => (Nil, maybeRuntimeError)
        case Right(instrumentations) => (instrumentations, None)
      }

      direct.modState(state =>
        state.copyAndSave(
          isRunning = false,
          outputs = state.outputs.copy(
            instrumentations = state.outputs.instrumentations ++ instr.toSet,
            runtimeError = runtimeError
          )
        ).copy(
          attachedDoms =
            attachedDoms.map(dom => (dom.getAttribute("uuid"), dom)).toMap
        )
      )
    }
  }
}
