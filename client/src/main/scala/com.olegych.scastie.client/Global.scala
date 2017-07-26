package com.olegych.scastie
package client

import components.Scastie

import scala.scalajs.js

import org.scalajs.dom.raw.HTMLElement

import api._
import japgolly.scalajs.react._

import upickle.default.{read => uread}

object Global {
  type Scope = BackendScope[Scastie, ScastieState]

  private var scope0: Option[Scope] = _

  def subscribe(scope: Scope): Unit = {
    scope0 = Some(scope)
  }

  def error(er: js.Error): Unit = {
    scope0.foreach { scope =>
      val direct = scope.withEffectsImpure
      direct.modState(
        state =>
          state
            .copyAndSave(
              outputs = state.outputs.copy(
                runtimeError = Some(
                  RuntimeError(
                    message = er.toString,
                    line = None,
                    fullStack = ""
                  )
                )
              )
            )
            .setRunning(false)
      )
    }
  }

  def signal(instrumentationsRaw: String,
             attachedDoms: js.Array[HTMLElement]): Unit = {
    scope0.foreach { scope =>
      val direct = scope.withEffectsImpure
      val result = uread[Either[Option[RuntimeError], List[Instrumentation]]](
        instrumentationsRaw
      )

      val (instr, runtimeError) = result match {
        case Left(maybeRuntimeError) => (Nil, maybeRuntimeError)
        case Right(instrumentations) => (instrumentations, None)
      }

      direct.modState(
        state =>
          state
            .copyAndSave(
              outputs = state.outputs.copy(
                instrumentations = state.outputs.instrumentations ++ instr.toSet,
                runtimeError = runtimeError
              )
            )
            .setRunning(false)
            .copy(
              attachedDoms =
                AttachedDoms(
                  attachedDoms.map(dom => (dom.getAttribute("uuid"), dom)).toMap
                )
          )
      )
    }
  }
}
