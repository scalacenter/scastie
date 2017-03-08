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

  def signal(instrumentationsF: js.Function0[String], attachedF: js.Function0[js.Array[HTMLElement]]): Unit = {
    scope0.foreach{ scope =>
      val direct = scope.accessDirect
      try {
        val instrumentations = uread[List[Instrumentation]](instrumentationsF())
        println(instrumentations)

        val attachedDoms = attachedF()
        println(attachedDoms.map(_.getAttribute("uuid")))

        direct.modState(state =>
          state.copyAndSave(
            attachedDoms = attachedDoms.map(dom => (dom.getAttribute("uuid"), dom)).toMap,
            outputs = state.outputs.copy(
              instrumentations = state.outputs.instrumentations ++ instrumentations.toSet
            )
          )
        )         
      } catch {
        case ex: Exception => {
          // TODO: some issue getting exception here 
          // https://github.com/scala-js/scala-js/issues/2788
          direct.modState(state =>
            state.copyAndSave(
              outputs = state.outputs.copy(
                runtimeError = RuntimeError.fromTrowable(ex, fromScala = true)
              )
            )
          )
        }
      }
    }
  }
}
