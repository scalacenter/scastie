package com.olegych.scastie.instrumentation

import scala.meta.inputs.Position

sealed trait EmptyResult

object EmptyResult {
  case object Unchanged extends EmptyResult
  case object NoMatch extends EmptyResult
  def unchanged: Either[EmptyResult, Position] = Left(Unchanged)
  def noMatch: Either[EmptyResult, Position] = Left(NoMatch)
}

