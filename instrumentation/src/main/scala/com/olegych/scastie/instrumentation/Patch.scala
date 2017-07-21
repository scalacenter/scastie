package com.olegych.scastie.instrumentation

import scala.collection.immutable.Seq
import scala.meta._
import scala.meta.tokens.Token

case class Patch(from: Token, to: Token, replace: String) {
  def insideRange(token: Token): Boolean =
    (token.input eq from.input) &&
      token.end <= to.end &&
      token.start >= from.start

  val tokens: scala.Seq[Token] = replace.tokenize.get.tokens.toSeq
  def runOn(str: Seq[Token]): Seq[Token] = {
    str.flatMap {
      case `from`              => tokens
      case x if insideRange(x) => Nil
      case x                   => Seq(x)
    }
  }
}

object Patch {
  def verifyPatches(patches: Seq[Patch]): Unit = {
    // TODO(olafur) assert there's no conflicts.
  }
  def apply(input: Seq[Token], patches: Seq[Patch]): String = {
    verifyPatches(patches)
    // TODO(olafur) optimize, this is SUPER inefficient
    patches
      .foldLeft(input) {
        case (s, p) => p.runOn(s)
      }
      .map(_.syntax)
      .mkString("")
  }
}
