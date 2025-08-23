package com.olegych.scastie.instrumentation

import scala.meta.Token
import scala.meta.prettyprinters._

/** A pair of tokens that align with each other across two different files */
case class MatchingToken(original: Token, revised: Token) {
  override def toString: String =
    s"${original.structure} <-> ${revised.structure}"
}