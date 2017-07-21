package com.olegych.scastie
package client

import scala.scalajs.js
import js.annotation.ScalaJSDefined
import js.UndefOr

import api.{Inputs, SnippetId, SnippetUserPart}

@ScalaJSDefined
trait EmbeddedOptionsJs extends js.Object {
  val base64UUID: UndefOr[String]
  val user: UndefOr[String]
  val update: UndefOr[Int]
  val worksheetMode: UndefOr[Boolean]
  val code: UndefOr[String]
  val targetType: UndefOr[String]
  val scalaVersion: UndefOr[String]
  val sbtConfig: UndefOr[String]
}

object EmbeddedOptions {
  def empty: EmbeddedOptions = EmbeddedOptions(None, None)
  def fromJs(options: EmbeddedOptionsJs): EmbeddedOptions = {
    import options._

    EmbeddedOptions(
      inputs = code.toOption.map(c => Inputs.default.copy(code = c)),
      snippetId = base64UUID.toOption.map(
        uuid =>
          SnippetId(uuid,
                    user.toOption
                      .map(u => SnippetUserPart(u, update.toOption)))
      )
    )
  }
}

case class EmbeddedOptions(snippetId: Option[SnippetId], inputs: Option[Inputs]) {
  def hasCode: Boolean = inputs.map(!_.code.isEmpty).getOrElse(false)
  def setCode(code: String): EmbeddedOptions = {
    val inputs0 = inputs.getOrElse(Inputs.default)
    copy(inputs = Some(inputs0.copy(code = code)))
  }
}
