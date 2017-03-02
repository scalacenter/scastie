package com.olegych.scastie
package client

import api._
import japgolly.scalajs.react._

object Global {

  type Scope = BackendScope[App.Props, App.State]

  private var scope0: Option[Scope] = _

  def subsribe(scope: Scope): Unit = {
    scope0 = Some(scope)
  }

  def signal(instrumentations: List[Instrumentation]): Unit = {
    scope0.foreach{ scope =>
      val direct = scope.accessDirect
      direct.modState(state =>
        state.copyAndSave(
          outputs = state.outputs.copy(
            instrumentations = state.outputs.instrumentations ++ instrumentations.toSet
          )
        )
      )
    }
  }
}
