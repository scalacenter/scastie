package com.olegych.scastie
package client

@ScalaJSDefined
trait EmbededOptionsJs extends js.Object {
  val base64UUID: UndefOr[String]
  val user: UndefOr[String]
  val update: UndefOr[Int]
  val worksheetMode: UndefOr[Boolean]
  val code: UndefOr[String]
  val targetType: UndefOr[String]
  val scalaVersion: UndefOr[String]
  val sbtConfig: UndefOr[String]
}

object EmbededOptions {
  def empty: EmbededOptions = EmbededOptions(None, None)
  def fromJs(options: EmbededOptionsJs): EmbededOptions = {
    import options._

    EmbededOptions(
      inputs = code.toOption.map(c => Inputs.default.copy(code = c)),
      snippetId = 
        base64UUID.toOption.map(uuid => 
          SnippetId(uuid, user.toOption.map(u => SnippetUserPart(u, update.toOption)))
        )
    )
  }
}

case class EmbededOptions(snippetId: Option[SnippetId], inputs: Option[Inputs]) {
  def hasCode: Boolean = inputs.map(!_.code.isEmpty).getOrElse(false)
  def setCode(code: String): EmbededOptions = {
    val inputs0 = inputs.getOrElse(Inputs.default)
    copy(inputs = Some(inputs0.copy(code = code)))
  }
}
