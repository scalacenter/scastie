package com.olegych.scastie
package client

import api.{SnippetId, ScalaTargetType}

import japgolly.scalajs.react.extra.router.RouterCtl

object AppProps {
  def default(router: RouterCtl[Page]) =  
    AppProps(
      router = Some(router),
      snippetId = None,
      embedded = None,
      targetType = None
    )
}

case class AppProps(
    router: Option[RouterCtl[Page]],
    snippetId: Option[SnippetId],
    embedded: Option[EmbededOptions],
    targetType: Option[ScalaTargetType]
) {
  def isEmbedded: Boolean = embedded.isDefined
}
