package com.olegych.scastie
package client

import scala.scalajs.js

import org.scalajs.dom.raw.HTMLDivElement 

import api._
import japgolly.scalajs.react._

import upickle.default.{read => uread}

object Global {

  type Scope = BackendScope[App.Props, App.State]

  private var scope0: Option[Scope] = _
  private var scalajsPlayground: Option[HTMLDivElement] = _

  def subsribe(scope: Scope): Unit = {
    scope0 = Some(scope)
  }

  def setScalaJsPlayground(element: HTMLDivElement): Unit = {
    scalajsPlayground = Some(element)    
  }

  def signal(instrumentationsF: js.Function0[String]): Unit = {
    scope0.foreach{ scope =>
      val direct = scope.accessDirect
      try {
        val instrumentations = uread[List[Instrumentation]](instrumentationsF())

        direct.modState(state =>
          state.copyAndSave(
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
                runtimeError = Some(RuntimeError(ex.toString, None, ""))
              )
            )
          )
        }
      }
    }
  }
}
