package com.olegych.scastie
package client

import components.Scastie

import scala.scalajs.js

import org.scalajs.dom.raw.HTMLElement

import api._
import japgolly.scalajs.react._

import play.api.libs.json.Json

// XXX: This should not be global
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

      val result =
        Json
          .fromJson[ScalaJsResult](
            Json.parse(instrumentationsRaw)
          )
          .asOpt

      val (instr, runtimeError) = result.map(_.in) match {
        case Some(Left(maybeRuntimeError)) => (Nil, maybeRuntimeError)
        case Some(Right(instrumentations)) => (instrumentations, None)
        case _                             => (Nil, None)
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
              attachedDoms = AttachedDoms(
                attachedDoms.map(dom => (dom.getAttribute("uuid"), dom)).toMap
              )
          )
      )
    }
  }
}
