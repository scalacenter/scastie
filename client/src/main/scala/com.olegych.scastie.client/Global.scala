package com.olegych.scastie.client

import com.olegych.scastie.api._
import com.olegych.scastie.client.components.Scastie

import scala.scalajs.js
import scala.collection.mutable.{Map => MMap}
import scala.util.{Try, Failure, Success}

import org.scalajs.dom.HTMLElement

import japgolly.scalajs.react._

import play.api.libs.json.Json

import java.util.UUID

object Global {
  type Scope = BackendScope[Scastie, ScastieState]

  private val scopes: MMap[UUID, Scope] = MMap()

  def subscribe(scope: Scope, id: UUID): Unit = {
    scopes(id) = scope
  }

  def unsubscribe(id: UUID): Unit = {
    scopes -= id
  }

  def error(er: js.Error, rawId: String): Unit = {
    withScope(rawId)(
      _.withEffectsImpure.modState(
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
    )
  }

  def signal(instrumentationsRaw: String, attachedDoms: js.Array[HTMLElement], rawId: String): Unit = {

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

    withScope(rawId)(
      _.withEffectsImpure.modState(
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
              attachedDoms = attachedDoms.map(dom => (dom.getAttribute("uuid"), dom)).toMap
          )
      )
    )
  }

  private def withScope(rawId: String)(body: Scope => Unit): Unit = {
    Try(UUID.fromString(rawId)) match {
      case Success(id) => {
        scopes.get(id).foreach { scope =>
          body(scope)
        }
      }
      case Failure(e) => e.printStackTrace()
    }
  }
}
